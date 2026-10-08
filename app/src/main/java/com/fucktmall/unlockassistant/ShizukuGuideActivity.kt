package com.fucktmall.unlockassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import com.fucktmall.unlockassistant.ui.ShizukuGuideScreen
import com.fucktmall.unlockassistant.ui.UnlockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shizuku 引导页外壳（UI 在 [ShizukuGuideScreen]，Compose + Material 3）。
 *
 * 入口：向导第 1 步的「通过Shizuku开启无障碍（推荐）」与设置面板第一张卡里同一个按钮。
 * 单独一个 Activity 而不是弹窗：这一页要放视频教程入口和一张可全屏放大的路径图，长度和交互都不适合弹窗。
 */
class ShizukuGuideActivity : ComponentActivity() {

    /** resume 时 +1，让这一页重新读一遍 Shizuku 状态（用户可能刚从 Shizuku / 系统设置回来）。 */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnlockTheme {
                ShizukuGuideScreen(
                    resumeTick = resumeTick.intValue,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 这一页每次回到前台都重读一遍状态；顺手把判定的原始输入打进日志
        // （只读，见 ShizukuA11y.logDiagnostics 的注释）。用户从授权框 / Shizuku 回来时，
        // 日志里就有"当时到底查到了什么"。
        // ⚠️ 必须在后台线程：诊断会起一个远端进程（最多等 4 秒），放主线程就是一次 ANR。
        lifecycleScope.launch(Dispatchers.IO) { ShizukuA11y.logDiagnostics(this@ShizukuGuideActivity) }
        resumeTick.intValue++
    }
}
