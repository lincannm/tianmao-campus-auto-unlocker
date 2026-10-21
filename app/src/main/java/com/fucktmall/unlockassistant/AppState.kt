package com.fucktmall.unlockassistant

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 全局开关与状态判定。
 *
 * 持久化标志：onboarded（是否走完首次使用向导；没走完时打开App 只弹 toast 指路，
 * 见 [MainActivity.onCreate]）、unlock_on_app / unlock_on_widget（两个代按开关，见下）、
 * shizuku_keep（要不要用 Shizuku 把无障碍保持在开启状态，见下）。
 *
 * 「打开App 直接进开锁界面」固定常开；无障碍没开时不硬跳，先弹提示
 * （见 [MainActivity.tryFireUnlock]）。
 *
 * ⭐ 无障碍被系统清掉后**只在用户显式开启过 [isShizukuKeep] 时才写回**（见 [ShizukuA11y]）：
 * 写什么、什么时候写由本App 决定 —— 持有 `WRITE_SECURE_SETTINGS` 时自己写，没有时借 Shizuku 的
 * shell 身份写。**这条路没开过时本App 对系统设置只读**，授权没了只能提示用户去系统无障碍列表手动打开
 * （「划掉卡片」那条路径由服务声明 `feedbackAllMask` 挡住，根本不会被清）。
 */
object AppState {

    private const val PREF = "unlock_app"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_UNLOCK_ON_APP = "unlock_on_app"
    private const val KEY_UNLOCK_ON_WIDGET = "unlock_on_widget"
    private const val KEY_SHIZUKU_KEEP = "shizuku_keep"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isOnboarded(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ONBOARDED, v).apply()
    }

    /** 「打开 App 时自动点开锁」——管**桌面图标**那条入口。默认关：关着只送到门锁页，最后一下自己按。 */
    fun isUnlockOnApp(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UNLOCK_ON_APP, false)

    fun setUnlockOnApp(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_UNLOCK_ON_APP, v).apply()
    }

    /** 「点小部件后自动点开锁」——管**桌面小部件**那条入口。默认开：直达门锁页并代按一次「点击开锁」。 */
    fun isUnlockOnWidget(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UNLOCK_ON_WIDGET, true)

    fun setUnlockOnWidget(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_UNLOCK_ON_WIDGET, v).apply()
    }

    /**
     * 「用 Shizuku 保持无障碍开启」：开着时，每次打开本App 会检查无障碍，被关掉就把它写回来。
     *
     * **默认关** —— 关着的时候本App 对系统设置只读，与没装 Shizuku 时行为完全一致；
     * 这一条是用户亲手打开的知情选择：引导页那个动作按钮按一次就置位（见 `ui/ShizukuGuideScreen.kt`
     * 的 `runOne`），也是 [ShizukuA11y] 唯一的触发前提。
     */
    fun isShizukuKeep(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHIZUKU_KEEP, false)

    fun setShizukuKeep(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SHIZUKU_KEEP, v).apply()
    }

    /** 我们在系统无障碍列表里的那一项。 */
    fun selfA11yComponent(ctx: Context): ComponentName =
        ComponentName(ctx.packageName, UnlockAccessibilityService::class.java.name)

    /**
     * 无障碍服务是否已开启。
     *
     * **两个系统设置都要满足**，缺一个都不算开：
     * 1. `Settings.Secure.ACCESSIBILITY_ENABLED` = 1 —— 系统无障碍的**总开关**
     *    （「设置 → 无障碍」页首那一只）。它为 0 时框架根本不绑任何服务，
     *    此时 `ENABLED_ACCESSIBILITY_SERVICES` 里**仍然留着我们的条目**（强停 / 服务进程
     *    非正常死亡之后就是这样），只看那一项会把「全局已关、什么都点不了」误判成「开着」；
     * 2. `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 里有我们这一项 ——
     *    即「设置 → 无障碍 → 已下载的服务」里那只开关。
     *
     * 第 2 项里每个服务是一段 `包名/类名` 文本，而**类名有两种合法写法**：全名 `包名.类名`
     * 与短名 `.类名`（省略掉与包名相同的部分）。系统里存哪一种由写入方决定，同一个服务
     * 换个写法字面就不相等 —— 所以必须解析成组件再比，**不许拿字符串逐字比**：
     * 系统里存短名时会把自己判成「未开启」，于是拦下跳转、胶囊显示未开启。
     *
     * 不拿 `UnlockAccessibilityService.running` 兜底：真机上关掉服务后系统会把它从启用
     * 列表摘掉却不回调 `onUnbind`，那个标志会一直是 true —— 拿它当准就会在无障碍其实
     * 已关时照旧抛 deep link。它只能当「服务确实连着」的补充信息（[isServiceConnected]），
     * **不能用来否决系统设置**。
     */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        if (!isAccessibilityMasterOn(ctx)) return false
        val raw = try {
            Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (t: Throwable) {
            null
        } ?: return false
        val self = selfA11yComponent(ctx)
        return raw.split(':').any { ComponentName.unflattenFromString(it.trim()) == self }
    }

    /** 系统无障碍的**总开关**（`ACCESSIBILITY_ENABLED`）；读不到就按关处理。 */
    fun isAccessibilityMasterOn(ctx: Context): Boolean = try {
        Settings.Secure.getInt(
            ctx.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
    } catch (t: Throwable) {
        false
    }

    /**
     * 服务当前是否真的连着：设置里开着、这里是 false ⇒ 多半是服务被系统杀了还没重新绑上
     * （HyperOS 长期不用会回收）。只用于在设置面板上给一句提示，**不用它决定要不要跳转**
     * （见 [isAccessibilityEnabled]）。
     */
    fun isServiceConnected(): Boolean = UnlockAccessibilityService.running

    /**
     * 是否已加入电池优化白名单。界面上目前没有入口，判定与 [MainActivity.openBatterySettings] 保留备用。
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
