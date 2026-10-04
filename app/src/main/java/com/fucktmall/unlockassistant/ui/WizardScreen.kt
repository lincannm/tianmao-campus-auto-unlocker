package com.fucktmall.unlockassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fucktmall.unlockassistant.AppState
import com.fucktmall.unlockassistant.MainActivity
import com.fucktmall.unlockassistant.R
import com.fucktmall.unlockassistant.Session

/**
 * 初次使用向导：2 步，每步「一句标题 + 两行正文 + 一个状态 + 一个按钮」。
 *
 * 3.2 起是 **2 步**：用户要求删掉原来的第 2 步「允许后台运行」（理由：省电策略看起来不会轻易杀掉
 * 本App，真出问题再把它加回来）。界面上仍然保留 [AppState.isIgnoringBatteryOptimizations] 与
 * [MainActivity.openBatterySettings]，加回来的成本就是再写一个 `StepBattery`。
 *
 * 3.0 的三点改动（都是用户要求）：
 *  - 标题从应用名改成 **[R.string.wiz_title]「初次使用向导」**（用户要求）；
 *  - **这一步已经达成时，动作按钮变灰不可用**（无障碍开了，「去开启」就没必要再点；
 *    电池白名单放行了，「去设置」同理）——靠 [PrimaryButton] 的 enabled 实现；
 *  - 「以后想改设置」从正文下面的灰色小字，变成一张**独立的主题色卡片** [NoticeCard]。
 *
 * ## 它是怎么被打开的（3.0 改了）
 *
 * **不再**由桌面图标直接打开 —— 首次打开本App 只弹一句 toast（见 [MainActivity]），
 * 向导的入口是**长按桌面图标 → 设置**（静态快捷方式 → [com.fucktmall.unlockassistant.SettingsActivity]）。
 * 这样用户是被迫自己找到设置入口的，这正是这块改动的目的：让他知道设置在哪。
 * 所以向导用 [WizardHost] 包一层：它读状态、写偏好，由 SettingsActivity 挂在未 onboarded 时显示。
 */
@Composable
fun WizardScreen(
    a11yOn: Boolean,
    onOpenA11y: () -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    Screen {
        ScreenHeader(
            title = stringResource(R.string.wiz_title),
            subtitle = stringResource(R.string.wiz_step_fmt, step + 1, 2)
        )
        StepBars(total = 2, current = step)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            StepCard {
                when (step) {
                    0 -> StepA11y(a11yOn = a11yOn, onOpenA11y = onOpenA11y)
                    else -> StepUsage()
                }
            }

            // 「以后想改设置」单独一张卡，只在最后一步出现（那一步才在讲以后怎么用）。
            if (step == 1) {
                Spacer(Modifier.height(12.dp))
                NoticeCard(
                    title = stringResource(R.string.wiz_hint_title),
                    body = stringResource(R.string.wiz_hint_body)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedActionButton(
                text = stringResource(R.string.wiz_prev),
                modifier = Modifier.weight(1f),
                enabled = step > 0
            ) { if (step > 0) step-- }

            PrimaryButton(
                text = if (step < 1) stringResource(R.string.wiz_next) else stringResource(R.string.wiz_done),
                modifier = Modifier.weight(1f)
            ) { if (step < 1) step++ else onFinish() }
        }
    }
}

/**
 * 向导外壳：读状态、写偏好。
 *
 * 由 [com.fucktmall.unlockassistant.SettingsActivity] 在「还没走完向导」时显示；
 * [onFinish] 里**只退出，不打开天猫校园**（用户 3.0 要求）。
 */
@Composable
fun WizardHost(resumeTick: Int, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val a11yOn = remember(resumeTick) { AppState.isAccessibilityEnabled(ctx) }

    WizardScreen(
        a11yOn = a11yOn,
        onOpenA11y = { MainActivity.openAccessibilitySettings(ctx) },
        onFinish = {
            AppState.setOnboarded(ctx, true)
            Session.addLog("初次使用向导完成：只退出，不打开天猫校园")
            onFinish()
        }
    )
}

@Composable
private fun StepA11y(a11yOn: Boolean, onOpenA11y: () -> Unit) {
    StepTitle(stringResource(R.string.wiz_s1_title))
    Spacer(Modifier.height(12.dp))
    BodyText(stringResource(R.string.wiz_s1_body))
    Spacer(Modifier.height(16.dp))
    StatusChip(
        ok = a11yOn,
        okText = stringResource(R.string.wiz_s1_on),
        badText = stringResource(R.string.wiz_s1_off)
    )
    Spacer(Modifier.height(16.dp))
    // 已经开了就禁用：这一步做完了，不需要（也不应该）再去点一次。
    PrimaryButton(
        text = stringResource(R.string.wiz_s1_btn),
        enabled = !a11yOn,
        onClick = onOpenA11y
    )
    Spacer(Modifier.height(10.dp))
    HintText(stringResource(R.string.wiz_s1_hint))
}

@Composable
private fun StepUsage() {
    StepTitle(stringResource(R.string.wiz_s3_title))
    Spacer(Modifier.height(12.dp))
    BodyText(stringResource(R.string.wiz_s3_body))
}
