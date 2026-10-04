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
 * ⭐ 3.3：**从设置页的弹窗换成独立 Activity**（用户要求「不要弹窗，跳转到新的 activity」）。
 * 原来那个 `AboutDialog` 已经删掉，设置页顶卡里的「了解本App」现在改成 `startActivity`。
 *
 * 出入方式：只有设置页会拉起它（`exported=false`），返回键 = 顶部「‹ 返回」= `finish()`，
 * 退回设置页；设置页的 `onResume` 会 +1 重新读一遍状态，所以回来时面板是新的。
 *
 * 这里**不写任何开锁/无障碍逻辑**：整页是纯展示。
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
