package com.fucktmall.unlockassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fucktmall.unlockassistant.ui.A11yOffDialog
import com.fucktmall.unlockassistant.ui.NoTmallDialog
import com.fucktmall.unlockassistant.ui.Screen
import com.fucktmall.unlockassistant.ui.UnlockTheme
import java.net.URLEncoder

/**
 * 「抛 deep link」的结果。
 *
 * [A11Y_OFF] 单列一项的原因：无障碍没开时**不能照旧打开**。
 * 打开的话，天猫校园的开屏广告没人点掉，用户会看到「本该自动关掉却还停在屏幕上」
 * 的广告而困惑——那不是跳转失败，是我们明知故犯。
 */
enum class FireResult { FIRED, NO_TMALL, A11Y_OFF, FAILED }

/**
 * 桌面入口（UI 用 Jetpack Compose + Material 3 写）。
 *
 * 三条路径：
 *  1. **还没走初次使用向导** → **什么都不做**，只弹一句 toast：
 *     「长按桌面上的「开锁」图标 → 点「设置」」（然后退出）。
 *     3.0 起刻意不在这里直接展示向导：见下面「为什么」。
 *  2. **走完向导** → 直接抛 deep link 进开锁界面（真正的「打开App = 开锁界面」）。
 *     无障碍没开时会先弹提示（[A11yOffDialog]），由用户选「去开启」或「仍要打开」。
 *     3.2 起这是**唯一**的行为：原来「关掉自动跳转就进设置面板」那个开关已按用户要求删除。
 *  3. deep link 抛失败 → 落到设置面板（[SettingsActivity]），让用户看到日志。
 *
 * ## ⭐ 为什么首次打开只弹 toast（3.0 用户要求）
 *
 * 目的不是省事，而是**逼用户自己找到设置在哪里**：向导全都挂在「长按桌面图标 → 设置」
 * 这个入口后面（见 [SettingsActivity]），用户是被那句 toast 引着走一遍的，
 * 以后想改设置时他就知道该长按图标。所以这里**不画任何界面**，也不自动跳天猫校园。
 *
 * deep link 原理（逆向自天猫校园 5.7.2，逐层核对过）：
 *
 *   Intent(ACTION_VIEW, "tmallcampus://web/open?url=<编码后的 H5 地址>")
 *     -> com.tmall.campus.and.dp.DispatchActivity        （唯一 BROWSABLE 导出入口）
 *     -> DeepLink.handle(ctx, uri)
 *     -> Navigator.openPage(ctx, uri, null)              （scheme=tmallcampus 分支）
 *     -> Navigator.openNativeUri                        （host=web + path=/open 命中）
 *     -> Navigator.openWebUrl                           （取 query 的 url 参数、解码、递归 openPage）
 *     -> RouterExtensionsKt.openWebViewUrl -> DRouter "/web/open" + extra "url"
 *     -> 在天猫校园自己的 WebView 里打开该页面
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** 宿舍门锁 H5 页。来自实测可用的 deep link，页面本身由服务端下发。 */
        const val TARGET_URL =
            "https://biz.confong.cn/app/tmall-xiaoyuan/page-m-webview/doorLock" +
                    "?loginRequired=true&hideNavigator=true&statusBarMode=dark"

        const val TMALL_PKG = "com.tmall.campus.and"

        /**
         * 3.1 起小部件用的动作：小部件点一下 = 带着这个 action 启动本 Activity。
         * 用它区分「从小部件来」还是「从桌面图标来」——两条入口的默认行为不同
         * （小部件默认代按开锁、图标默认不代按，见 [AppState]）。
         */
        const val ACTION_WIDGET_UNLOCK = "com.fucktmall.unlockassistant.action.WIDGET_UNLOCK"

        /** 拼出实测可用的 deep link。 */
        fun buildDeepLink(url: String): String =
            "tmallcampus://web/open?url=" + URLEncoder.encode(url, "UTF-8")

        /**
         * 开始「自动跳过弹窗」的时间窗，并把界面送到开锁界面。设置页、自动跳转两处都用它。
         *
         * @param ignoreA11y 无障碍没开时是否仍然照抛。只有用户在提示里明确选了
         *   「仍要打开」才允许传 true —— 那是用户自己的知情选择。
         * @param armUnlock 这次跳转要不要授权服务**代按一次**「点击开锁」。
         *   3.1 起由设置里的两个开关决定：图标入口默认关、小部件入口默认开
         *   （[AppState.isUnlockOnApp] / [AppState.isUnlockOnWidget]）。
         *   关着 = 3.0 的老行为，只送到门锁页，最后一下由用户自己按。
         */
        fun tryFireUnlock(
            ctx: Context,
            source: String,
            ignoreA11y: Boolean = false,
            armUnlock: Boolean = false
        ): FireResult {
            if (!AppState.isPackageInstalled(ctx, TMALL_PKG)) {
                Session.addLog("没装天猫校园，无法跳转（$source）")
                return FireResult.NO_TMALL
            }
            if (!ignoreA11y && !AppState.isAccessibilityEnabled(ctx)) {
                Session.addLog("无障碍未开启，已拦下不跳转（$source）")
                return FireResult.A11Y_OFF
            }
            // 时间窗只在真的会抛 deep link 时开：拦下的时候开窗没有任何意义。
            Session.start(ctx)
            Session.armUnlock(ctx, armUnlock)
            Session.addLog("==== 开锁（$source，代按=${if (armUnlock) "开" else "关"}）====")
            return try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(buildDeepLink(TARGET_URL)))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                FireResult.FIRED
            } catch (e: Exception) {
                Session.addLog("抛 deep link 失败：${e.message}")
                FireResult.FAILED
            }
        }

        fun openAccessibilitySettings(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Session.addLog("打不开无障碍设置：${e.message}")
            }
        }

        /**
         * 申请忽略电池优化。优先用直接弹窗（ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS），
         * 失败就退回列表页。
         *
         * 3.2 起**界面上没有调用点**（向导的「允许后台运行」那一步按用户要求删了），
         * 留着是为了出问题时能把那一步原样加回来。
         */
        fun openBatterySettings(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: Exception) {
                // 落到下面的列表页
            }
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Session.addLog("打不开电池优化设置：${e.message}")
            }
        }

        fun openSettingsPanel(ctx: Context) {
            ctx.startActivity(
                Intent(ctx, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 从系统设置页回来时 +1，用来让 Compose 重新判定一次（用户去开了无障碍再回来就自动接着跳）。 */
    private val resumeTick = mutableIntStateOf(0)
    private var sawFirstResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 ⇒ Android 15+ 强制 edge-to-edge；Compose 侧用 WindowInsets 让开系统栏。
        enableEdgeToEdge()

        // ⭐ 首次使用：**不做任何动作**，只弹一句 toast 指路（用户 3.0 要求）。
        // 向导本体在「长按桌面图标 → 设置」里（SettingsActivity），不在这里。
        if (!AppState.isOnboarded(this)) {
            Session.addLog("还没走初次使用向导：只提示「长按图标 → 设置」，本App 不做任何动作")
            Toast.makeText(this, R.string.toast_first_run, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            UnlockTheme {
                EntryFlow(
                    resumeTick = resumeTick.intValue,
                    fromWidget = fromWidget,
                    onExit = { finish() }
                )
            }
        }
    }

    /** 这次启动是不是「点了桌面小部件」（小部件用 [ACTION_WIDGET_UNLOCK] 拉起本 Activity）。 */
    private val fromWidget: Boolean
        get() = intent?.action == ACTION_WIDGET_UNLOCK

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Activity 还活着时又点了一次小部件（例如正停在无障碍提示框上）：
        // recreate() 会带着新 intent 重跑 onCreate —— 判定逻辑只有一份，不另写一套。
        recreate()
    }

    override fun onResume() {
        super.onResume()
        // 第一次 resume 就是启动本身，不算「回来」，否则会重复判定一次。
        if (sawFirstResume) {
            resumeTick.intValue++
        } else {
            sawFirstResume = true
        }
    }
}

/**
 * 「直接开门」这条路径的画面。它几乎没有界面：
 *  - 一切正常 → 抛完 deep link 就 finish，用户看到的是天猫校园的开锁页；
 *  - 无障碍没开 → 停在提示对话框上（不抛），等用户选；
 *  - 天猫校园没装 → 同样只给一个提示。
 *
 * 从系统设置页回来会重新判定一次（[resumeTick] 变化）：用户去开了无障碍再回来，
 * 这里会**自动接着跳**，不用再点一次图标。
 */
@Composable
private fun EntryFlow(resumeTick: Int, fromWidget: Boolean, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var fired by remember { mutableStateOf(false) }
    var a11yPrompt by remember { mutableStateOf(false) }
    var noTmall by remember { mutableStateOf(false) }

    // 两条入口的「要不要代按开锁」来自两个独立开关（用户指定：图标默认关、小部件默认开）。
    val source = if (fromWidget) "小部件" else "自动跳转"
    val arm = if (fromWidget) AppState.isUnlockOnWidget(ctx) else AppState.isUnlockOnApp(ctx)

    LaunchedEffect(resumeTick) {
        if (fired) return@LaunchedEffect

        when (MainActivity.tryFireUnlock(ctx, source, armUnlock = arm)) {
            FireResult.FIRED -> {
                fired = true
                onExit()
            }
            FireResult.A11Y_OFF -> a11yPrompt = true
            FireResult.NO_TMALL -> noTmall = true
            FireResult.FAILED -> {
                MainActivity.openSettingsPanel(ctx)
                onExit()
            }
        }
    }

    // 空白 MD3 底色，对话框浮在上面。
    Screen { }

    if (a11yPrompt) {
        A11yOffDialog(
            onOpenSettings = { MainActivity.openAccessibilitySettings(ctx) },
            onOpenAnyway = {
                a11yPrompt = false
                fired = true
                MainActivity.tryFireUnlock(ctx, "仍要打开", ignoreA11y = true, armUnlock = arm)
                onExit()
            },
            onDismiss = {
                a11yPrompt = false
                onExit()
            }
        )
    }

    if (noTmall) {
        NoTmallDialog(
            onDismiss = {
                noTmall = false
                onExit()
            }
        )
    }
}
