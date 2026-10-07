package com.fucktmall.unlockassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fucktmall.unlockassistant.ui.UnlockTheme
import com.fucktmall.unlockassistant.ui.WidgetPinScreen

/**
 * 「小组件添加至桌面」引导页外壳（UI 在 [WidgetPinScreen]，Compose + Material 3）。
 *
 * 只有设置页会拉起它（`exported=false`）；返回键 = 顶栏「‹ 返回」= `finish()`。
 * 这里没有开锁 / 无障碍逻辑，只有「授桌面快捷方式权限」与「固定小部件」两件事。
 */
class WidgetPinActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnlockTheme {
                WidgetPinScreen(onBack = { finish() })
            }
        }
    }
}
