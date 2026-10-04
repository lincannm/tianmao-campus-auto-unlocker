package com.fucktmall.unlockassistant.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import com.fucktmall.unlockassistant.R
import java.nio.ByteBuffer
import java.nio.channels.Channels

/**
 * 「了解本App」页那张会动的插图怎么解出来。
 *
 * 单独放一个文件，是因为这里踩过**三个**坑，都在这台真机上打日志看清楚了：
 *
 * 1. **不能 `ctx.assets.open(...)`**：本工程**没有** `src/main/assets/` 目录，
 *    放那儿会在运行时 `FileNotFoundException`（被 catch 吞掉 ⇒ 图上少一张）。
 *    图要放 `res/raw/`。见 `docs/40-pitfalls.md`。
 * 2. **`BitmapFactory.decodeStream` 不够**：它对 `res/raw` 里的**动画 WebP** 实测返回的是
 *    静态 `BitmapDrawable`（日志原文：
 *    `draw mascot: android.graphics.drawable.BitmapDrawable intrinsic=640x852`），
 *    也就是只有第一帧 —— 用户看到的就是"不能动弹"。
 *    正路是 `ImageDecoder`（API 28+）：它解动画 WebP 给 `AnimatedImageDrawable`，
 *    `isRunning`/`start()` 都是现成的，[AboutScreen] 用 `withFrameNanos` 驱动它重画。
 * 3. **`ImageDecoder.createSource` 不收 `InputStream`**（只有 `ByteBuffer` / `File` 两个重载），
 *    而 `openRawResource` 那种流也不支持 seek。做法：把 raw 读进 `ByteBuffer`
 *    （`res/raw` 的流就是普通文件流），再交给 `createSource(ByteBuffer)`。
 *
 * 失败时逐级退到静态首帧（`il_about_mascot_still.png`），再失败就返回 null —— 调用方
 * 直接不画那张图，**不崩**。
 */
internal fun loadMascot(ctx: Context): Drawable? {
    animatedMascot(ctx)?.let { return it }
    for (resId in intArrayOf(R.raw.il_about_mascot, R.raw.il_about_mascot_still)) {
        try {
            ctx.resources.openRawResource(resId).use { decoded ->
                BitmapFactory.decodeStream(decoded)?.let { return it.toDrawable() }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "插图 raw#$resId 解码失败：${t.message}")
        }
    }
    return null
}

/** API 28+ 才有 `ImageDecoder`；更老的系统直接返回 null，由调用方退到静态图。 */
private fun animatedMascot(ctx: Context): Drawable? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return try {
        val bytes = ctx.resources.openRawResource(R.raw.il_about_mascot).use { it.readBytes() }
        // decodeDrawable 的第三参（listener）不传：默认行为就是"无限循环播放"。
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
    } catch (t: Throwable) {
        Log.w(TAG, "ImageDecoder 解动画插图失败（${t.javaClass.simpleName}）：${t.message}")
        null
    }
}

/** 让"没有 `ImageDecoder` 的老系统"也能拿到静态首帧。 */
@Suppress("DEPRECATION")
private fun Any.toDrawable(): Drawable? = when (this) {
    is Drawable -> this
    is Bitmap -> BitmapDrawable(android.content.res.Resources.getSystem(), this)
    else -> null
}

private const val TAG = "MascotAnimation"
