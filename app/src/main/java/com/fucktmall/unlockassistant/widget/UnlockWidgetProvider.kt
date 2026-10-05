package com.fucktmall.unlockassistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import com.fucktmall.unlockassistant.MainActivity
import com.fucktmall.unlockassistant.R
import com.fucktmall.unlockassistant.Session

/**
 * 「开锁」桌面小部件。
 *
 * 设计约定：**小部件 = 桌面图标**。点它只是带着
 * [MainActivity.ACTION_WIDGET_UNLOCK] 把 [MainActivity] 拉起来，后面判向导、判无障碍、
 * 抛 deep link、开 90 秒时间窗、清弹窗、代按开锁这一整套**全走同一条链路**，
 * 小部件自己不判断任何东西 —— 否则就有第二套逻辑，以后必然分叉。
 * 唯一的差别是「要不要代按一次开锁」：小部件入口看
 * [com.fucktmall.unlockassistant.AppState.isUnlockOnWidget]（默认开），
 * 图标入口看 [com.fucktmall.unlockassistant.AppState.isUnlockOnApp]（默认关）。
 *
 * 为什么是 RemoteViews 而不是 androidx.glance：小部件本来就是 RemoteViews 的封装，
 * 引 glance 要新增外部依赖，而这里只需要「一张卡 + 一只锁 + 一个点击目标」。
 *
 * 尺寸：四张独立卡（1×1 / 2×1 / 2×2 / 4×2），放置后都能自由缩放；
 * 缩放后按实际尺寸换布局档（见 [layoutFor]）。
 */
abstract class BaseUnlockWidgetProvider : AppWidgetProvider() {

    /** 拿不到实测尺寸时的兜底布局（每个入口一张，见四个子类）。 */
    protected abstract val fallbackLayout: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // 用户拖动边框改了尺寸：换到对应档位的布局（系统的尺寸信息就在 options 里）。
        render(context, manager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val opts = manager.getAppWidgetOptions(id)
        val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val layout = if (w > 0 && h > 0) layoutFor(w, h) else fallbackLayout

        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(R.id.widget_root, clickIntent(context))

        // 装饰与锁按实际尺寸等比缩放：布局里那些 dp 是「110dp 短边」标定的默认值，
        // 写死它们会让大卡显得空、小卡显得挤。
        val short = minOf(w, h).toFloat()
        val detail = when {
            short <= 0f -> "用布局默认尺寸"
            layout == R.layout.widget_unlock_square -> {
                resize(views, R.id.widget_badge, short * BADGE_RATIO)
                resize(views, R.id.widget_lock, short * LOCK_SMALL_RATIO)
                "托盘圆 ${fmt(short * BADGE_RATIO)}dp + 锁 ${fmt(short * LOCK_SMALL_RATIO)}dp"
            }
            layout == R.layout.widget_unlock_wide -> {
                resize(views, R.id.widget_ring, short * RING_RATIO)
                resize(views, R.id.widget_lock, short * LOCK_SMALL_RATIO)
                "细环 ${fmt(short * RING_RATIO)}dp + 锁 ${fmt(short * LOCK_SMALL_RATIO)}dp"
            }
            else -> {
                resize(views, R.id.widget_lock, short * LOCK_RATIO)
                "锁 ${fmt(short * LOCK_RATIO)}dp"
            }
        }

        manager.updateAppWidget(id, views)

        // 记一行日志：档位或尺寸算错了在桌面上很难看出来，
        // 有这行就能直接对上「桌面给了多大 → 用了哪张布局 → 元素多大」。
        Session.addLog("小部件 #$id 渲染：桌面给 ${w}×${h}dp → ${layoutName(layout)}，$detail")
    }

    /**
     * 运行时改视图尺寸。**API 31+ 才有** `RemoteViews.setViewLayoutWidth/Height`
     * （第三参是 `TypedValue` 的单位常量，不是 `MeasurementUnit` —— 后者在 SDK 里不存在）；
     * 更低版本退回布局里写死的 dp（那套值按 110dp 短边标定）。
     */
    private fun resize(views: RemoteViews, viewId: Int, dp: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        views.setViewLayoutWidth(viewId, dp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(viewId, dp, TypedValue.COMPLEX_UNIT_DIP)
    }

    private fun fmt(v: Float): String = String.format("%.0f", v)

    private fun clickIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_WIDGET_UNLOCK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            // targetSdk 36 必须显式给可变性；FLAG_IMMUTABLE 是这里唯一正确的选择。
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /**
         * 元素尺寸比例（都相对**卡片短边**）：
         *   裸锁 0.44；托盘圆 0.58 + 里面的锁 0.30；细环 0.70 + 里面的锁 0.30。
         */
        private const val LOCK_RATIO = 0.44f
        private const val BADGE_RATIO = 0.58f
        private const val RING_RATIO = 0.70f
        private const val LOCK_SMALL_RATIO = 0.30f

        /**
         * 按实际尺寸（dp）选布局档。
         *
         * ⚠️ 两条必须知道的约束：
         *  1. 桌面传进来的是**它实际分给这张卡的格子尺寸**（本机：1 格 ≈ 65–84dp、
         *     2 行 ≈ 184dp、4 格宽 ≈ 343dp），**不是**说明书里写的 110×40 / 250×110；
         *  2. 所以不能按写死的 dp 阈值判断宽卡：2×1 与 2 格宽在不同机型上宽度差别很大，
         *     固定阈值会把它们分错档。
         *
         * 判断按「行数 + 宽高比」：
         *   `twoRows`：1 行约 65–110dp、2 行约 130–220dp，取中间值 130 当分界；
         *   `sideways`：宽大于高 1.35 倍 = 横条 / 宽卡；
         *   `tallNarrow`：高 ≥ 宽的 1.4 倍 = 竖长条 —— 这种卡片上放「托盘圆」会孤零零
         *   浮在中间一大片空白里，所以退回**裸锁**。
         */
        fun layoutFor(widthDp: Int, heightDp: Int): Int {
            val tallNarrow = heightDp >= widthDp * 1.4
            val twoRows = heightDp >= 130
            val sideways = widthDp > heightDp * 1.35
            return when {
                tallNarrow -> R.layout.widget_unlock_1x1
                twoRows && sideways -> R.layout.widget_unlock_wide
                twoRows -> R.layout.widget_unlock_square
                sideways -> R.layout.widget_unlock_bar
                else -> R.layout.widget_unlock_1x1
            }
        }

        /** 日志里用的布局名（别在日志里打资源 id，那个数字认不出来）。 */
        fun layoutName(layout: Int): String = when (layout) {
            R.layout.widget_unlock_1x1 -> "1×1 裸锁"
            R.layout.widget_unlock_bar -> "横条 裸锁"
            R.layout.widget_unlock_square -> "2×2 托盘圆"
            else -> "宽卡 细环"
        }
    }
}

/** 1×1：只有一只锁（裸双色锁，无光环）。 */
class UnlockWidget1x1 : BaseUnlockWidgetProvider() {
    override val fallbackLayout: Int = R.layout.widget_unlock_1x1
}

/** 2×1：横条，同样只画锁。 */
class UnlockWidget2x1 : BaseUnlockWidgetProvider() {
    override val fallbackLayout: Int = R.layout.widget_unlock_bar
}

/** 2×2：浅红托盘圆 + 小锁。 */
class UnlockWidget2x2 : BaseUnlockWidgetProvider() {
    override val fallbackLayout: Int = R.layout.widget_unlock_square
}

/** 4×2：细描边圆环 + 小锁。 */
class UnlockWidget4x2 : BaseUnlockWidgetProvider() {
    override val fallbackLayout: Int = R.layout.widget_unlock_wide
}
