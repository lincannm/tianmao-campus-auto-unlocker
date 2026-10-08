package com.fucktmall.unlockassistant

import android.app.Activity
import android.os.Bundle
import android.provider.Settings

/**
 * ⚠️ **实验包专用（3.11-exp1）的隐藏测试入口，不进交付物。**
 *
 * 它存在的唯一目的：回答"**不经过 Shizuku，本App 自己能不能写 `Settings.Secure`**"。
 * 做法是给自己设一个自造的键（`unlockassistant_probe`，不属于任何系统功能），
 * 再读回来核对 —— 写成功就证明这条权限真的生效了（`Settings.Secure.putString` 没权限时
 * 只是静默失败，不看返回值根本发现不了）。
 *
 * **怎么用它**（不需要点手机界面，adb 拉起来就行）：
 * `adb shell am start -n com.fucktmall.unlockassistant/.PermProbeActivity`
 * 结果同时进 logcat（tag `UnlockAssistant`）与设置里，用
 * `adb shell settings get secure unlockassistant_probe` 核对原值。
 *
 * **不带界面**：`finish()` 在 `onCreate` 里就调了，屏幕上不会留下任何东西。
 * 交付前这个文件与清单里对应的 `<activity>` 都要删掉。
 */
class PermProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_GRANT -> runGrant()
            else -> runWrite()
        }
        finish()
    }

    /**
     * 直接写：验证"本App 自己有没有那条权限"。
     * ⚠️ **必须后台线程**：`Settings.Secure.putString` 是 binder 调用，主线程做会卡界面
     * （这也是 `adb shell am start` 拉起它时最容易踩的坑）。
     */
    private fun runWrite() = Thread {
        val key = "unlockassistant_probe"
        val value = "ok-" + System.currentTimeMillis()
        val writeError = try {
            Settings.Secure.putString(contentResolver, key, value)
            null
        } catch (t: Throwable) {
            t.javaClass.simpleName + ": " + t.message
        }
        // 读回来核对（读任何人都能做，所以"读得到写进去的值"才说明写成功了）。
        val readBack = try {
            Settings.Secure.getString(contentResolver, key)
        } catch (t: Throwable) {
            "读失败 " + t.javaClass.simpleName
        }
        Session.addLog(
            "权限探针（自己写）：" + if (readBack == value) "成功" else "失败" +
                "（写入异常=${writeError ?: "无"} 读回=$readBack）"
        )
    }.start()

    /** 让 Shizuku 以 shell 身份 `pm grant` 那条权限：验证"能不能借它拿到自己的写回能力"。 */
    private fun runGrant() = Thread {
        val ok = ShizukuA11y.grantWriteSecureSettings(this)
        Session.addLog("权限探针（Shizuku 授权）：${if (ok) "成功" else "失败"}")
    }.start()

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_GRANT = "grant"
    }
}
