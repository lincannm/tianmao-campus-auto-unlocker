package com.fucktmall.unlockassistant

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
 * 入口是**长按桌面图标 → 设置**（静态快捷方式，见 res/xml/shortcuts.xml），
 * 以及 deep link 抛失败时的落地页。
 *
 * ## ⭐ 3.0：初次使用向导也挂在这里
 *
 * 用户要求「首次打开本App 不做任何动作，只弹 toast 让他自己长按图标进设置」，
 * 所以向导不再由 [MainActivity] 展示，而是**在这里**：
 * 还没 onboarded 时（或用户点了面板里的「重进初次使用向导」）直接显示向导。
 * 向导走完就 `finish()` —— **退出，不打开天猫校园**（用户要求）。
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
                            // 这里只退出：用户明确要求不要在向导结束时打开天猫校园。
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
