package com.fucktmall.unlockassistant

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 全局开关与状态判定。
 *
 * 持久化标志：
 *  - onboarded：是否走完首次使用向导。没走完时打开App **只弹一句 toast 指路**
 *    （见 [MainActivity.onCreate]），向导本体在「长按桌面图标 → 设置」里。
 *  - unlock_on_app / unlock_on_widget：两个「代按开锁」开关（见下面各自的说明）。
 *
 * 「打开App 直接进开锁界面」**3.2 起固定常开**：原来那个 `auto_jump` 偏好和它的开关
 * 已按用户要求删除（连带 [MainActivity] 里「关掉就进设置面板」那条分支）。
 * 无障碍没开时**仍然不会**硬跳，会先弹提示（见 [MainActivity.tryFireUnlock]）。
 *
 * ## ⭐ 3.0：本App 现在**只读**系统设置，不再写
 *
 * 2.1 加过一套「被系统清掉无障碍授权后，下次冷启动自动写回」（类名 `A11yGuard`），
 * 它需要 `WRITE_SECURE_SETTINGS`（只有 adb / Shizuku / root 能给）。
 * **用户要求去掉这个功能**，所以 3.0 把 `A11yGuard.kt`、清单里的那条权限、
 * 设置面板上的开关、「已自动恢复」的 toast 全部删除，判定逻辑收敛回这里
 * （只有一个地方读系统设置，见 [isAccessibilityEnabled]）。
 *
 * 代价要说清楚：授权一旦被**除「划掉卡片」以外**的路径清掉（锁屏清理 / 强力清理 /
 * 系统回收…），本App 不会自己写回来，只能提示用户去系统无障碍列表里手拨一次。
 * 「划掉卡片」那条路径由服务声明 `feedbackAllMask` 挡住（见
 * `UnlockAccessibilityService.onServiceConnected`），不需要写回。
 */
object AppState {

    private const val PREF = "unlock_app"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_UNLOCK_ON_APP = "unlock_on_app"
    private const val KEY_UNLOCK_ON_WIDGET = "unlock_on_widget"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isOnboarded(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ONBOARDED, v).apply()
    }

    /**
     * 「打开 App 时自动点开锁」——管**桌面图标**那条入口。默认关：
     * 关着就是 3.0 的老行为，只把界面送到门锁页，最后一下自己按。
     */
    fun isUnlockOnApp(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UNLOCK_ON_APP, false)

    fun setUnlockOnApp(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_UNLOCK_ON_APP, v).apply()
    }

    /**
     * 「点小部件后自动点开锁」——管**桌面小部件**那条入口。默认开：
     * 点小部件直达门锁页并代按一次「点击开锁」。
     */
    fun isUnlockOnWidget(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UNLOCK_ON_WIDGET, true)

    fun setUnlockOnWidget(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_UNLOCK_ON_WIDGET, v).apply()
    }

    /** 我们在系统无障碍列表里的那一项，格式与系统一致：`包名/服务类全名`。 */
    fun selfA11yComponent(ctx: Context): String =
        "${ctx.packageName}/${UnlockAccessibilityService::class.java.name}"

    /**
     * 无障碍服务是否已开启。
     *
     * **唯一权威来源是系统设置** `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`，
     * 也就是用户在「设置 → 无障碍」里开关的那一项。
     *
     * 为什么**不**用 `UnlockAccessibilityService.running` 兜底（曾经因此出过一个真 bug）：
     * 真机上把服务关掉后，系统会把它从启用列表里摘掉，但**没有回调 onUnbind**
     * ——内存日志里只有「无障碍服务已连接」、没有「已断开」，那个静态标志一直是 true。
     * 于是 App 以为无障碍还开着，照旧抛 deep link：用户看到本该被点掉的开屏广告
     * 还停在屏幕上，正是那次要修的问题（实测复现过，见 work/v2/06_settings_logcheck.png）。
     *
     * 内存标志最多只能当「服务确实连着」的补充信息（[isServiceConnected]），
     * **不能用来否决系统设置**。
     */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val raw = try {
            Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (t: Throwable) {
            null
        } ?: return false
        val needle = selfA11yComponent(ctx)
        return raw.split(':').any { it.trim().equals(needle, ignoreCase = true) }
    }

    /**
     * 服务当前是否真的连着。
     *
     * 设置里开着、这里却是 false ⇒ 多半是服务被系统杀了还没重新绑上
     * （HyperOS 长期不用会回收）。用来在设置面板上给一句提示，
     * **不用它来决定要不要跳转**（见 [isAccessibilityEnabled] 的说明）。
     */
    fun isServiceConnected(): Boolean = UnlockAccessibilityService.running

    /**
     * 是否已加入电池优化白名单。
     *
     * 3.2 起**界面上没有入口了**：向导第 2 步「允许后台运行」按用户要求删除（用户判断省电策略
     * 不会轻易杀掉本App）。判定与 [MainActivity.openBatterySettings] 都留着，
     * 真出问题时把那个步骤写回来即可。
     */
    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (t: Throwable) {
            false
        }
    }

    fun isPackageInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (t: Throwable) {
        false
    }
}
