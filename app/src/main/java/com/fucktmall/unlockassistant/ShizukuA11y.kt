package com.fucktmall.unlockassistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

/**
 * 「用 Shizuku 把无障碍在开启状态维持住」的全部代码（可选增强）。
 *
 * **为什么需要它**：`force-stop` 会把本App 的无障碍授权一起清掉并持久化
 * （AOSP 的 `AccessibilityManagerService.onPackagesForceStoppedLocked`，没有任何声明能豁免），
 * 而把开关写回系统设置要 `WRITE_SECURE_SETTINGS` —— 本App **不申请**这条权限，
 * 改成借 Shizuku 的 shell 身份去执行 `settings put secure …`。
 * **分工要说准**：读设置（普通 App 就能读）、算出该写什么（我们自己的组件追加进列表）、
 * 发起调用，都是本App 做的；Shizuku 只出借它的 shell 身份跑那一条 `settings` 命令，
 * 因此本App 自己**从头到尾不持有** `WRITE_SECURE_SETTINGS`，清单里也依旧没有它。
 *
 * **触发时机只有一处**：打开本App 时先看一眼无障碍，被关掉就写回来
 * （见 [MainActivity] 的入口流程）。强停之后本App 的任何后台代码都跑不了，
 * 「用户完全不碰本App 也能自愈」需要常驻守护进程，那是另一层，这里不做。
 *
 * **三条硬约束（改这段代码必须全部满足）**：
 * 1. **只有用户亲手打开过「用 Shizuku 保持无障碍开启」才写**（[AppState.isShizukuKeep]）；
 *    没开过这个开关时本App 对系统设置**只读**，行为与没有 Shizuku 时一模一样；
 * 2. **只往列表里加我们自己的那一项**：别人的无障碍服务原样保留，**永远不删别人的条目**，
 *    也不动 `accessibility_enabled` 以外的任何键；
 * 3. **写不进去就返回 false**，由调用方照旧提示「去系统设置里手动打开」，不许假装成功。
 */
object ShizukuA11y {

    /** Shizuku 管理器的包名。**只用来「打开 Shizuku」**，判定状态不靠它（见 [state]）。 */
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    /**
     * Shizuku 的**下载直链**：直接落到 GitHub Releases 的资产列表。
     * 不给官网，是因为官网首页还要用户自己找下载入口，而这条路本来就是可选增强，少一步是一步。
     */
    const val SHIZUKU_DOWNLOAD_URL = "https://github.com/thedjchi/Shizuku/releases"

    /** 请求授权用的 requestCode；结果走 [Shizuku.addRequestPermissionResultListener]。 */
    const val REQUEST_CODE = 4210

    /** `settings` 的可执行文件写绝对路径：不依赖 Shizuku 那边进程的 PATH。 */
    private const val SETTINGS_BIN = "/system/bin/settings"

    /** `pm` 同上。 */
    private const val PM_BIN = "/system/bin/pm"

    /**
     * 写系统设置要的那条权限。它的 protectionLevel 是
     * `signature|privileged|development|installer|role` —— 带 `development` 标记，
     * 所以 **adb / shell（uid 2000，Shizuku 的服务就是 shell）可以用 `pm grant` 授给声明过它的 App**。
     * 拿到之后本App 自己就能写 `Settings.Secure`，**不再需要 Shizuku 当时在跑**。
     */
    const val PERMISSION_WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

    /** 界面上的三条状态（**没有「装没装」这一条**，理由见 [state]）。 */
    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    /**
     * 当前状态（**只描述"能不能借 Shizuku 的 shell 身份跑命令"**，与那条一次性权限无关）。
     * **刻意不判断「Shizuku 装没装」**：
     * Android 11+ 的包可见性让「查不到」和「没装」分不开，而各家发行版的包名又不一致，
     * 一旦查不到就会把**已经装了**的人引到「先去装一个」，比不判断更糟。
     * 所以只问 binder 与能力，装没装由用户自己看引导页第 1 步。
     *
     * ⚠️ **`Shizuku.checkSelfPermission()` 只在"能力探测失败"之后才作数**，这是刻意的：
     * 它是个把异常也吞成"没授权"的布尔，实测出现过"系统里已授权、它也返回 DENIED"的假阴性
     * （那会把已经能用的人钉在「还没允许」那一态）。真正的判据是**能不能以 shell 身份跑一条命令**；
     * 只有跑不动时才回去问权限，用来分辨「没授权」和「服务不在」。
     *
     * 探测走 [probeShellNow] 的缓存（5 秒），所以这个函数可以每秒被界面调用；
     * 冷启动那一次请先在 IO 线程预热缓存（见 [logDiagnostics]）。
     *
     * ⚠️ **这条路开着不等于能写**：拿过 [PERMISSION_WRITE_SECURE_SETTINGS] 的机器上，
     * 写回完全不经过 Shizuku（见 [ensureEnabled]），那时本函数返回什么与写回能力无关。
     */
    fun state(ctx: Context): State = when {
        !binderAlive() -> State.NOT_RUNNING
        probeShellNow().isEmpty() -> State.READY
        hasPermission() -> State.NOT_RUNNING
        else -> State.NO_PERMISSION
    }

    /** 用户允许「开锁」用 Shizuku 了吗（只用来给失败分因，见 [state]）。 */
    private fun hasPermission(): Boolean = readCheckSelfPermission().first == true

    /**
     * `Shizuku.checkSelfPermission()` 的原值：第一项是 GRANTED/DENIED，第二项是异常文本。
     * **单独留一份**，因为抛异常与"没授权"在这一页上长得一样，诊断日志要能把两者分开。
     */
    private fun readCheckSelfPermission(): Pair<Boolean?, String> = try {
        (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) to ""
    } catch (t: Throwable) {
        null to (t.javaClass.simpleName + ": " + t.message)
    }

    /**
     * 探测用的那一条命令：`id -u` 是 toybox 自带、只读、任何权限下都能跑，退出码 0 即"通了"。
     * 用它而不是查权限，是因为**"能不能跑"才是这条路真正需要的事实**。
     */
    private val PROBE_CMD = arrayOf("/system/bin/id", "-u")

    /** 探测结果（**内存缓存**，不落盘）：`""` = 通，非空 = 上次的失败原因。 */
    @Volatile
    private var probeError = ""

    /** 上次探测的 `System.currentTimeMillis()`；`0` = 本次进程里还没探过。 */
    @Volatile
    private var probeAt = 0L

    /**
     * 界面上每秒都会问一次状态，而探测要起一个远端进程 —— 所以结果缓存 [PROBE_TTL_MS]。
     *
     * ⚠️ **首次调用是真跑**（可能阻塞几秒），所以界面上不要在组合 / 主线程里"第一次"探它：
     * 页面 `onResume` 时先在 IO 线程调一次 [logDiagnostics]（它内部强制探）把缓存预热，
     * 之后组合期读到的都是缓存。超时也从 [PROBE_TIMEOUT_MS] 收到 1.5 秒，免得真卡住时拖太久。
     *
     * @param force 忽略缓存重探一次（用户按下按钮、页面刚回来时用）。
     * @return 失败原因（空串 = 通）。
     */
    fun probeShellNow(force: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!force && probeAt != 0L && now - probeAt < PROBE_TTL_MS) return probeError
        val r = runAsShell(PROBE_CMD, PROBE_TIMEOUT_MS)
        probeError = if (r.ok) {
            ""
        } else {
            "exit=${r.exitCode}${if (r.stderr.isNotBlank()) " " + r.stderr else ""}"
        }
        probeAt = now
        return probeError
    }

    /** 探测命令的超时：它只是起个进程，超了就按"不通"算。 */
    private const val PROBE_TIMEOUT_MS = 1500L

    /** 探测结果的新鲜期。 */
    private const val PROBE_TTL_MS = 5000L

    /**
     * 把判定的原始输入打进日志（tag `UnlockAssistant`）。**会在 IO 线程起一次远端探测**，
     * 调用方负责放到后台线程。
     *
     * **为什么要留着**：`Shizuku.checkSelfPermission()` 是个全捕获的布尔，
     * "抛异常"与"没授权"在界面上长得一模一样；而且它和"实际能不能跑命令"会不一致。
     * 这三项只有都打出来才分得清是哪一种。只读，不改任何状态。
     */
    fun logDiagnostics(ctx: Context) {
        val probe = probeShellNow(force = true)
        val (self, selfErr) = readCheckSelfPermission()
        val selfAnswer = when {
            self == true -> "GRANTED"
            self == false -> "DENIED"
            else -> "抛 $selfErr"
        }
        val ctxAnswer = runCatching {
            val r = androidx.core.content.ContextCompat.checkSelfPermission(ctx, PERMISSION_API_V23)
            if (r == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED($r)"
        }.getOrElse { "抛 " + it.javaClass.simpleName + ": " + it.message }
        val uid = runCatching { Shizuku.getUid().toString() }.getOrElse { "抛 " + it.javaClass.simpleName }
        val ver = runCatching { Shizuku.getVersion().toString() }.getOrElse { "抛 " + it.javaClass.simpleName }
        Session.addLog(
            "Shizuku 诊断：pingBinder=${binderAlive()} " +
                "远端跑命令=${if (probe.isEmpty()) "通" else "不通($probe)"} " +
                "checkSelfPermission=$selfAnswer ContextCompat(本进程)=$ctxAnswer " +
                "getUid=$uid 远端版本=$ver 本App uid=${ctx.applicationInfo.uid} 状态=${state(ctx)}"
        )
    }

    /** 授权用的权限名（Shizuku 的 provider 声明它，管理器按它授权给调用方）。 */
    private const val PERMISSION_API_V23 = "moe.shizuku.manager.permission.API_V23"

    /** binder 到了没有 = Shizuku 服务在不在跑（它在非 root 机上重启手机后要重新启动一次）。 */
    fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /**
     * 弹 Shizuku 自己的授权框。**必须在界面上调用**（向导 / 设置页），
     * 结果异步回来，调用方挂 [Shizuku.OnRequestPermissionResultListener] 刷新状态。
     */
    fun requestPermission() {
        if (!binderAlive()) return
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            Session.addLog("Shizuku 授权请求失败：${t.message}")
        }
    }

    /**
     * 打开 Shizuku 管理器（用户要在里面把服务启动起来）。
     *
     * 找不到它的启动入口时退回[打开下载页][openDownloadPage]：Android 11+ 的包可见性 + 各家
     * 发行版包名不同，都可能让这里 [PackageManager.getLaunchIntentForPackage] 返回 null，
     * 那也不该变成一个点了没反应的按钮。
     */
    fun openShizuku(ctx: Context): Boolean {
        val i = try {
            ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        } catch (t: Throwable) {
            null
        }
        if (i == null) return openDownloadPage(ctx)
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (t: Throwable) {
            Session.addLog("打不开 Shizuku：${t.message}")
            false
        }
    }

    /** 用浏览器打开 Shizuku 的下载直链（GitHub Releases）。 */
    fun openDownloadPage(ctx: Context): Boolean = openUrl(ctx, SHIZUKU_DOWNLOAD_URL)

    /** 把一个 http(s) 链接交给浏览器（或能处理它的 App）；打不开返回 false，由界面给一句 toast。 */
    fun openUrl(ctx: Context, url: String): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        Session.addLog("打不开浏览器：${t.message}")
        false
    }

    /**
     * 无障碍关着就写回开启，**已是开启状态直接返回 true 不动系统设置**。
     *
     * 写两步：①把我们的组件追加进 `enabled_accessibility_services`（已经在里面就跳过）；
     * ②`accessibility_enabled=1`。**谁来写有两档，优先第一档**：
     *
     * 1. **本App 自己写**（[hasWriteSecureSettings] 为真时）：用户在引导页用 Shizuku 授过一次
     *    `WRITE_SECURE_SETTINGS` 之后就走这条 —— **完全不碰 Shizuku**，因此与"Shizuku 服务在不在跑"
     *    无关，[state] 返回什么也不影响这条路；
     * 2. **借 Shizuku 的 shell 身份写**：没那条权限时的退路（要 Shizuku 当时在跑）。
     *
     * ⚠️ `Settings.Secure` 的**读**普通 App 就能做，所以列表是本地读出来合并的，
     * **耗时操作，调用方要放到 IO 线程**。
     *
     * @return 调用结束时无障碍是不是开的（本来就开着也算 true）。
     */
    fun ensureEnabled(ctx: Context, timeoutMs: Long = 6000L): Boolean {
        if (AppState.isAccessibilityEnabled(ctx)) return true

        // 第一档：本App 自己写。**这里绝不能先判 [state]** —— 那条路本来就不经过 Shizuku，
        // 加了判断就等于"拿过权限也白拿"。
        if (hasWriteSecureSettings(ctx)) {
            val self = AppState.selfA11yComponent(ctx)
            val current = readEnabledServices(ctx)
            val present = current.split(':').any {
                ComponentName.unflattenFromString(it.trim()) == self
            }
            if (!present) {
                val short = self.flattenToShortString()
                val next = if (current.isBlank()) short else "$current:$short"
                if (!putSecure(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)) return false
            }
            return putSecure(ctx, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        }

        // 没有那条权限：退回借 Shizuku 的 shell 身份写（要 Shizuku 当时在跑）。
        if (state(ctx) != State.READY) {
            Session.addLog("Shizuku 不可用（${state(ctx)}），不动无障碍设置")
            return false
        }

        val self = AppState.selfA11yComponent(ctx)
        val current = readEnabledServices(ctx)
        val present = current.split(':').any {
            ComponentName.unflattenFromString(it.trim()) == self
        }
        if (!present) {
            val short = self.flattenToShortString()
            val next = if (current.isBlank()) short else "$current:$short"
            if (!settingsPut(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next, timeoutMs)) {
                return false
            }
        }
        return settingsPut(ctx, Settings.Secure.ACCESSIBILITY_ENABLED, "1", timeoutMs)
    }

    /** 本App 自己有没有 `WRITE_SECURE_SETTINGS`（有就能直接写，不必借 Shizuku）。 */
    fun hasWriteSecureSettings(ctx: Context): Boolean = try {
        ctx.checkSelfPermission(PERMISSION_WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /**
     * **让 Shizuku 替本App 授 `WRITE_SECURE_SETTINGS`**（一次就够，之后不再依赖 Shizuku 在跑）。
     *
     * 它执行的是 `/system/bin/pm grant <本App> android.permission.WRITE_SECURE_SETTINGS`；
     * 这条权限带 `development` 保护标记，所以 shell 身份有资格授它。
     * ⚠️ 前提是**清单里声明过它** —— 没声明时 `pm grant` 会报 `Unknown permission`。
     *
     * 这个动作是"给本App 补一条权限"，不是"用户手动去系统里开开关"，所以不会撞上侧载 App 的
     * 「受限设置」拦截（那条拦截挡的是用户手拨开关那条路）。
     *
     * **耗时操作（起远端进程），调用方要放到 IO 线程**。
     */
    fun grantWriteSecureSettings(ctx: Context, timeoutMs: Long = 6000L): Boolean {
        if (hasWriteSecureSettings(ctx)) return true
        if (!binderAlive()) {
            Session.addLog("授权失败：Shizuku 服务没在运行")
            return false
        }
        val r = runAsShell(
            arrayOf(PM_BIN, "grant", ctx.packageName, PERMISSION_WRITE_SECURE_SETTINGS),
            timeoutMs
        )
        val ok = r.ok && hasWriteSecureSettings(ctx)
        Session.addLog(
            if (ok) {
                "已用 Shizuku 给本App 授 WRITE_SECURE_SETTINGS（之后不再依赖 Shizuku 在跑）"
            } else {
                "用 Shizuku 授 WRITE_SECURE_SETTINGS 失败（exit=${r.exitCode}）：${r.stderr}"
            }
        )
        return ok
    }

    /** 用本App 自己的权限写一条 `Settings.Secure`（没有权限时 `putString` 会静默失败，所以读回核对）。 */
    private fun putSecure(ctx: Context, key: String, value: String): Boolean {
        val ok = try {
            Settings.Secure.putString(ctx.contentResolver, key, value) &&
                Settings.Secure.getString(ctx.contentResolver, key) == value
        } catch (t: Throwable) {
            Session.addLog("自己写 $key 失败：${t.javaClass.simpleName}: ${t.message}")
            return false
        }
        Session.addLog(if (ok) "自己写回系统设置：$key" else "自己写 $key 失败（权限已失效？）")
        return ok
    }

    /** 系统设置里那串启用列表（`包名/类名:包名/类名`），读不到就当空。 */
    private fun readEnabledServices(ctx: Context): String = try {
        Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
    } catch (t: Throwable) {
        ""
    }

    private fun settingsPut(ctx: Context, key: String, value: String, timeoutMs: Long): Boolean {
        val r = runAsShell(arrayOf(SETTINGS_BIN, "put", "secure", key, value), timeoutMs)
        Session.addLog(
            if (r.ok) {
                "用 Shizuku 写回系统设置：$key${if (r.stderr.isBlank()) "" else "（stderr：${r.stderr}）"}"
            } else {
                "用 Shizuku 写 $key 失败（exit=${r.exitCode}）：${r.stderr}"
            }
        )
        return r.ok
    }

    /** 一条远端命令的结果：`ok` 只看退出码；`stderr` 留着给日志（失败时就是失败原因）。 */
    class ShellResult(val ok: Boolean, val exitCode: Int, val stderr: String)

    /**
     * 让 Shizuku 以 shell 身份执行一条命令（远端进程，参数按数组传，不经过 shell 解析，
     * 所以含 `:` 的启用列表不需要转义）。
     *
     * ⚠️ `Shizuku.newProcess` 在 api 13.1.5 里是 **private**（`javap` 核过），
     * 官方注释写着「计划在 API 14 移除」，所以只能反射调用；一旦哪天真没了，
     * 反射会抛异常 → 返回失败 → 退回到「让用户自己去系统设置里打开」，
     * 不会静默地假装成功。
     */
    private fun runAsShell(cmd: Array<String>, timeoutMs: Long): ShellResult {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, cmd, null, null) as? ShizukuRemoteProcess
                ?: return ShellResult(false, -1, "newProcess 返回 null")

            val finished = proc.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            val code = if (finished) proc.exitValue() else -1
            val err = try {
                proc.errorStream.bufferedReader().readText().trim().take(160)
            } catch (t: Throwable) {
                ""
            }
            proc.destroy()
            // 超时（code = -1）时 stderr 把原因说清楚，别让它看起来像"命令返回了 -1"。
            val text = if (!finished && err.isBlank()) "等待超时（${timeoutMs}ms）" else err
            ShellResult(code == 0, code, text)
        } catch (t: Throwable) {
            ShellResult(false, -1, t.javaClass.simpleName + ": " + t.message)
        }
    }
}
