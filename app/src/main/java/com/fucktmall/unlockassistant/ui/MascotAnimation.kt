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
 * 「了解本App」页那张会动的插图怎么解出来：`ImageDecoder` → `BitmapFactory` → 静态兜底，
 * 逐级退，全解不出来就返回 null，调用方不画那张图，不崩。
 *
 * 1. 资源放 **`res/raw/`**：本工程没有 `src/main/assets/` 源集，`ctx.assets.open` 会在运行时
 *    `FileNotFoundException`。
 * 2. `BitmapFactory.decodeStream` 对动画 WebP 只给第一帧（静态 `BitmapDrawable`），
 *    所以 API 28+ 优先用 `ImageDecoder` 解出 `AnimatedImageDrawable`，`isRunning`/`start()`
 *    都是现成的，[AboutScreen] 用 `withFrameNanos` 驱动它重画。
 * 3. `ImageDecoder.createSource` 不收 `InputStream`（只有 `ByteBuffer`/`File`），而
 *    `openRawResource` 的流不支持 seek —— 所以先读进 `ByteBuffer` 再交给 `createSource`。
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

/** API 28+ 才有 `ImageDecoder`；老系统返回 null，由调用方退到静态图。 */
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

/** 把解码结果包成 `Drawable`。 */
@Suppress("DEPRECATION")
private fun Any.toDrawable(): Drawable? = when (this) {
    is Drawable -> this
    is Bitmap -> BitmapDrawable(android.content.res.Resources.getSystem(), this)
    else -> null
}

private const val TAG = "MascotAnimation"
