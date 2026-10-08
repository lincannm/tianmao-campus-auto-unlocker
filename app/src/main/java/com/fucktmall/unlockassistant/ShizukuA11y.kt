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

    /** 界面上的三条状态（**没有「装没装」这一条**，理由见 [state]）。 */
    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    /**
     * 当前状态。**刻意不判断「Shizuku 装没装」**：
     * Android 11+ 的包可见性让「查不到」和「没装」分不开，而各家发行版的包名又不一致，
     * 一旦查不到就会把**已经装了**的人引到「先去装一个」，比不判断更糟。
     * 所以只问 binder（服务在不在跑，与包名无关）+ 授权，装没装由用户自己看引导页第 1 步。
     */
    fun state(ctx: Context): State = when {
        !binderAlive() -> State.NOT_RUNNING
        !hasPermission() -> State.NO_PERMISSION
        else -> State.READY
    }

    /** binder 到了没有 = Shizuku 服务在不在跑（它在非 root 机上重启手机后要重新启动一次）。 */
    fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** 用户允许「开锁」用 Shizuku 了吗。 */
    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
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
     * ②`accessibility_enabled=1`。两步都由 Shizuku 以 shell 身份执行。
     *
     * ⚠️ `Settings.Secure` 的**读**普通 App 就能做，所以列表是本地读出来合并的，
     * 只有写走 Shizuku。**耗时操作，调用方要放到 IO 线程**。
     *
     * @return 调用结束时无障碍是不是开的（本来就开着也算 true）。
     */
    fun ensureEnabled(ctx: Context, timeoutMs: Long = 6000L): Boolean {
        if (AppState.isAccessibilityEnabled(ctx)) return true
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
        val ok = runAsShell(arrayOf(SETTINGS_BIN, "put", "secure", key, value), timeoutMs)
        Session.addLog(if (ok) "用 Shizuku 写回系统设置：$key" else "用 Shizuku 写 $key 失败")
        return ok
    }

    /**
     * 让 Shizuku 以 shell 身份执行一条命令（远端进程，参数按数组传，不经过 shell 解析，
     * 所以含 `:` 的启用列表不需要转义）。
     *
     * ⚠️ `Shizuku.newProcess` 在 api 13.1.5 里是 **private**（`javap` 核过），
     * 官方注释写着「计划在 API 14 移除」，所以只能反射调用；一旦哪天真没了，
     * 反射会抛异常 → 返回 false → 退回到「让用户自己去系统设置里打开」，
     * 不会静默地假装成功。
     */
    private fun runAsShell(cmd: Array<String>, timeoutMs: Long): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, cmd, null, null) as? ShizukuRemoteProcess ?: return false

            val finished = proc.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            val code = if (finished) proc.exitValue() else -1
            if (code != 0) {
                val err = try {
                    proc.errorStream.bufferedReader().readText().trim().take(120)
                } catch (t: Throwable) {
                    ""
                }
                Session.addLog("Shizuku 执行失败（exit=$code）：$err")
            }
            proc.destroy()
            code == 0
        } catch (t: Throwable) {
            Session.addLog("Shizuku 执行异常：${t.message}")
            false
        }
    }
}
