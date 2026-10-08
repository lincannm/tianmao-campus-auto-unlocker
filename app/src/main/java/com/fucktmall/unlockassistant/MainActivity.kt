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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * 打开App 这条路上等 Shizuku 写回无障碍开关的上限。
 *
 * 开门是「现在就要用」，不能为了写一次设置把人晾在跳转前面；实测一次写入是百毫秒级，
 * 这个值只是兜底（Shizuku 没在跑时 [ShizukuA11y.ensureEnabled] 会立刻返回，不会等）。
 */
private const val SHIZUKU_WRITE_TIMEOUT_MS = 1500L

/**
 * 「抛 deep link」的结果。
 *
 * [A11Y_OFF] 单列一项：无障碍没开时**不能照旧打开**，
 * 否则开屏广告没人点掉，用户会停在一个本该被自动关掉的广告上。
 */
enum class FireResult { FIRED, NO_TMALL, A11Y_OFF, FAILED }

/**
 * 桌面入口（UI 用 Jetpack Compose + Material 3 写）。
 *
 * 还没走完向导时这里什么都不做，只弹一句 toast 指路（向导挂在「长按桌面图标 → 设置」入口
 * 后面）；其余情况抛 deep link 进开锁界面，无障碍没开就先弹 [A11yOffDialog] 让用户选，
 * 抛失败则落到 [SettingsActivity] 看日志。
 *
 * deep link 必须走天猫校园唯一的 BROWSABLE 导出入口 `com.tmall.campus.and.dp.DispatchActivity`
 * （`tmallcampus://web/open?url=<编码后的 H5 地址>`），它解析出 query 里的 url 后在自家
 * WebView 里加载该 H5。
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** 宿舍门锁 H5 页；页面内容由服务端下发。 */
        const val TARGET_URL =
            "https://biz.confong.cn/app/tmall-xiaoyuan/page-m-webview/doorLock" +
                    "?loginRequired=true&hideNavigator=true&statusBarMode=dark"

        const val TMALL_PKG = "com.tmall.campus.and"

        /** 小部件启动本 Activity 时带的 action：用它区分两条入口（见 [AppState]）。 */
        const val ACTION_WIDGET_UNLOCK = "com.fucktmall.unlockassistant.action.WIDGET_UNLOCK"

        /** 拼出天猫校园能识别的 deep link。 */
        fun buildDeepLink(url: String): String =
            "tmallcampus://web/open?url=" + URLEncoder.encode(url, "UTF-8")

        /**
         * 开始「自动跳过弹窗」的时间窗，并把界面送到开锁界面。设置页、自动跳转两处都用它。
         *
         * @param ignoreA11y 无障碍没开时是否仍然照抛。只有用户在提示里明确选了
         *   「仍要打开」才允许传 true —— 那是用户自己的知情选择。
         * @param armUnlock 这次跳转要不要授权服务**代按一次**「点击开锁」，
         *   由 [AppState.isUnlockOnApp] / [AppState.isUnlockOnWidget] 决定；
         *   关着就只送到门锁页，最后一下由用户自己按。
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
         * 申请忽略电池优化：优先用直接弹窗（ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS），
         * 失败就退回列表页。目前界面上没有调用点。
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
                // 直接申请弹窗失败（部分机型没这个入口），落到下面的列表页。
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

        // ⭐ 首次使用：**不做任何动作**，只弹 toast 指路；向导在「长按桌面图标 → 设置」（SettingsActivity）。
        // 刻意不在这里直接显示向导：让用户自己走一遍那个入口，以后想改设置时才找得到它。
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
 * 「直接开门」的画面：无障碍没开就停在提示对话框上等用户选，天猫校园没装也只给一个提示。
 *
 * [resumeTick] 变化会重新判定一次：用户去开了无障碍再回来就**自动接着跳**，不用再点图标。
 *
 * 判定之前还有一步（只在用户开过「用 Shizuku 保持无障碍开启」时发生）：让 Shizuku 把被系统
 * 清掉的无障碍写回来。它跑在 IO 线程、有超时，失败也不影响下面的判定。
 */
@Composable
private fun EntryFlow(resumeTick: Int, fromWidget: Boolean, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var fired by remember { mutableStateOf(false) }
    var a11yPrompt by remember { mutableStateOf(false) }
    var noTmall by remember { mutableStateOf(false) }

    // 两条入口的代按开关相互独立：图标默认关、小部件默认开。
    val source = if (fromWidget) "小部件" else "自动跳转"
    val arm = if (fromWidget) AppState.isUnlockOnWidget(ctx) else AppState.isUnlockOnApp(ctx)

    LaunchedEffect(resumeTick) {
        if (fired) return@LaunchedEffect

        // ⭐ 无障碍被系统清掉、而用户开过「用 Shizuku 保持无障碍开启」时：先用 Shizuku 把它写回来，
        // 用户就感觉不到被清过（force-stop 之后本App 没有别的机会补救，这是唯一的一处）。
        // 写不成功 / Shizuku 不在，就什么都不做 —— 下面照旧拦下并弹 [A11yOffDialog]。
        if (AppState.isShizukuKeep(ctx) && !AppState.isAccessibilityEnabled(ctx)) {
            withContext(Dispatchers.IO) {
                ShizukuA11y.ensureEnabled(ctx, SHIZUKU_WRITE_TIMEOUT_MS)
            }
        }

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

    // 这一屏平时没有界面（成功就直接 finish 了），只铺一层空白 MD3 底色，
    // 让 [A11yOffDialog] / [NoTmallDialog] 浮在上面时有正确的背景色。
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
