package com.fucktmall.unlockassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fucktmall.unlockassistant.ui.AboutScreen
import com.fucktmall.unlockassistant.ui.UnlockTheme

/**
 * 「了解本App」整页外壳（UI 在 [AboutScreen]，Compose + Material 3）。
 *
 * 只有设置页会拉起它（`exported=false`）；返回键 = 顶部「‹ 返回」= `finish()`，
 * 设置页的 `onResume` 会 +1 重新读一遍状态。这里**不写任何开锁/无障碍逻辑**，是纯展示。
 */
class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnlockTheme {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}
