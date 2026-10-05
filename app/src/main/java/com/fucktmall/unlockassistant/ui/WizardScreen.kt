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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * ## ⭐ 3.4：无障碍没拿到就**走不完向导**（用户要求）
 *
 * 用户实测反馈：第一次进向导、无障碍根本没开，却能一路点「下一步」到「完成」。
 * 这是错的 —— 向导第 1 步存在的唯一目的就是让用户把无障碍打开，
 * 允许空着手走到最后，等于向导白走一遍，而且 App 之后还会因为无障碍没开而拦下跳转
 * （见 [MainActivity.tryFireUnlock] 返回 `A11Y_OFF` 那条路径），用户会以为"向导走完了却不能用"。
 *
 * 做法：**「下一步」/「完成」的 `enabled` 绑定 [a11yOn]**，没开就点不动；
 * 同时在按钮上方用 error 色说明为什么点不动（[R.string.wiz_need_a11y]）。
 * 不另加"跳过"入口 —— 那正好是用户要去掉的行为。想退出向导只能用系统返回键。
 *
 * ## 3.4：无障碍那一步**自带开启教程**（用户要求「附上教程」）
 *
 * 用 [StepsCard] 画成带序号的清单，文案见 `strings.xml` 的 `wiz_s1_tut_*`。
 * **必须是跨品牌的通用说法**（用户：『用户不一定是小米手机』）：系统的无障碍页可能叫
 * 「无障碍」也可能叫「辅助功能」，服务列表可能叫「已下载的应用」也可能叫「已安装的服务」——
 * 两个名字都写出来，别把本机（HyperOS）那套路径当成所有人的路径。
 * 差异说明也**并进各步骤正文**，卡底下不再挂灰色小字（用户要求）。
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

    // 最后一步的下标。3.4 起「能不能往下走」= 无障碍开没开（见类注释）。
    val lastStep = 1
    val canAdvance = a11yOn

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

            // 教程卡只在无障碍那一步出现：用户点完「去开启」就离开本App 了，
            // 回来时靠的就是这张卡记着刚才该点哪里。
            if (step == 0) {
                Spacer(Modifier.height(12.dp))
                StepsCard(
                    title = stringResource(R.string.wiz_s1_tut_title),
                    steps = listOf(
                        stringResource(R.string.wiz_s1_tut_1),
                        stringResource(R.string.wiz_s1_tut_2),
                        stringResource(R.string.wiz_s1_tut_3),
                        stringResource(R.string.wiz_s1_tut_4),
                        stringResource(R.string.wiz_s1_tut_5)
                    )
                )
            }

            // 「以后想改设置」单独一张卡，只在最后一步出现（那一步才在讲以后怎么用）。
            if (step == lastStep) {
                Spacer(Modifier.height(12.dp))
                NoticeCard(
                    title = stringResource(R.string.wiz_hint_title),
                    body = stringResource(R.string.wiz_hint_body)
                )
            }
        }

        // 按钮为什么是灰的 —— 3.4 起必须写出来，否则用户只会觉得"卡住了"。
        if (!canAdvance) {
            Text(
                text = stringResource(R.string.wiz_need_a11y),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.error
            )
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
                text = if (step < lastStep) {
                    stringResource(R.string.wiz_next)
                } else {
                    stringResource(R.string.wiz_done)
                },
                modifier = Modifier.weight(1f),
                // ⭐ 没拿到无障碍就点不动（3.4 用户要求，见类注释）。
                enabled = canAdvance
            ) { if (step < lastStep) step++ else onFinish() }
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

/**
 * 第 1 步：开启无障碍。
 *
 * 卡里只放「是什么 / 现在什么状态 / 按钮」这三件事，**具体怎么点放卡外的 [StepsCard]** ——
 * 用户点完按钮就跳去系统设置了，教程卡留在屏幕上（回来时还在），卡里塞满步骤反而没人读。
 */
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
}

@Composable
private fun StepUsage() {
    StepTitle(stringResource(R.string.wiz_s3_title))
    Spacer(Modifier.height(12.dp))
    BodyText(stringResource(R.string.wiz_s3_body))
}
