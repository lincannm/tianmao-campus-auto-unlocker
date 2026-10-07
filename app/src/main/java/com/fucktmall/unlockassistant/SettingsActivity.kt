package com.fucktmall.unlockassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.fucktmall.unlockassistant.ui.SettingsScreen
import com.fucktmall.unlockassistant.ui.UnlockTheme
import com.fucktmall.unlockassistant.ui.WizardHost

/**
 * 设置 / 控制面板外壳（UI 在 [SettingsScreen]，Compose + Material 3）。
 *
 * 入口：**长按桌面图标 → 设置**（静态快捷方式，见 res/xml/shortcuts.xml），以及
 * deep link 抛失败时的落地页。初次使用向导也挂在这里（还没 onboarded，或点了面板里的
 * 「重进初次使用向导」时直接显示）；向导走完就 `finish()`，不打开天猫校园。
 */
class SettingsActivity : ComponentActivity() {

    /** resume 时 +1，让面板重新读一遍无障碍状态（用户可能刚从系统设置页回来）。 */
    private val resumeTick = mutableIntStateOf(0)

    /** 是否正在显示向导。进入时按偏好定一次；「重进向导」按钮就地切过来。 */
    private var wizardMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        wizardMode = !AppState.isOnboarded(this)

        setContent {
            UnlockTheme {
                if (wizardMode) {
                    WizardHost(
                        resumeTick = resumeTick.intValue,
                        onFinish = {
                            // 偏好已由 WizardHost 写完（onboarded=true）。
                            // 这里只退出：向导结束时不开天猫校园。
                            finish()
                        }
                    )
                } else {
                    SettingsScreen(
                        resumeTick = resumeTick.intValue,
                        onOpenA11y = { MainActivity.openAccessibilitySettings(this) },
                        onOpenWizard = {
                            // 重走一遍向导：就地切过去，不用回桌面。
                            AppState.setOnboarded(this, false)
                            wizardMode = true
                        },
                        onOpenWidgetPin = {
                            // 去独立整页：那里才有地方讲清权限与手动添加的办法。
                            startActivity(Intent(this, WidgetPinActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}
