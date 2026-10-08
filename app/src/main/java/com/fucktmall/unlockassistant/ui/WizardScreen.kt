package com.fucktmall.unlockassistant.ui

import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
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
 * 入口是**长按桌面图标 → 设置**（静态快捷方式 → [com.fucktmall.unlockassistant.SettingsActivity]）；
 * 首次打开本App 只弹一句 toast（见 [MainActivity]），用户得自己找到设置入口。
 * [WizardHost] 包一层：它读状态、写偏好，SettingsActivity 在未 onboarded 时挂上它。
 *
 * ⭐ **无障碍没开启就走不完向导**：「下一步」/「完成」的 `enabled` 绑定 [a11yOn]，没开就点不动，
 * 并在按钮上方用 error 色说明原因（[R.string.wiz_need_a11y]）。第 1 步存在的唯一目的就是让用户
 * 把无障碍打开；允许空着手走到最后，向导等于白走一遍，之后跳转还会被 [MainActivity.tryFireUnlock]
 * 以 `A11Y_OFF` 拦下，用户会以为"走完了却不能用"。**没留跳过入口是有意的**，退出只能用返回键。
 *
 * 无障碍那一步自带开启教程（[StepsCard] 的带序号清单）。教程文案**必须跨品牌通用**：
 * 系统的无障碍页可能叫「无障碍」也可能叫「辅助功能」，服务列表可能叫「已下载的应用」
 * 也可能叫「已安装的服务」，两个名字都要写出来，不能只写某一个品牌的路径。
 *
 * 教程卡**上面**还有一张 [TroubleshootCard]（侧载 App 被系统拒绝授权的弹窗怎么处理）：
 * 它讲的是"照教程做却被拦住"这种情况，所以与正常步骤分开、单独一张不同色的卡，
 * 并且**默认折叠**（点标题展开）—— 碰到那个弹窗的人是少数，摊开会把「怎么开启」挤出首屏。
 */
@Composable
fun WizardScreen(
    a11yOn: Boolean,
    onOpenA11y: () -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    // 排查卡默认收起（用户要求）；点标题展开。放手 remember 而不是卡片内部：换步时卡片会离开组合，
    // 状态在这里才留得住，而且"默认折叠"这条只在这一处写。
    var troubleOpen by remember { mutableStateOf(false) }

    // 最后一步的下标；「能不能往下走」= 无障碍开没开（见上方注释）。
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

            // 排查卡挂在教程卡**上面**、默认折叠：它是"万一被拦住"的分支，碰到的人是少数，
            // 摊开会把每个人都要走的「怎么开启」挤到屏幕下面。展开状态归这里持有，跨重组不丢。
            if (step == 0) {
                TroubleshootCard(
                    title = stringResource(R.string.wiz_s1_tb_title),
                    collapsedHint = stringResource(R.string.wiz_s1_tb_collapsed),
                    imageDesc = stringResource(R.string.wiz_s1_tb_img_desc),
                    body = stringResource(R.string.wiz_s1_tb_body),
                    steps = listOf(
                        stringResource(R.string.wiz_s1_tb_1),
                        stringResource(R.string.wiz_s1_tb_2),
                        stringResource(R.string.wiz_s1_tb_3),
                        stringResource(R.string.wiz_s1_tb_4)
                    ),
                    hint = stringResource(R.string.wiz_s1_tb_hint),
                    expanded = troubleOpen,
                    onToggle = { troubleOpen = !troubleOpen }
                )

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

            // 「以后想改设置」单独一张卡，只挂在最后一步（那一步才在讲以后怎么用）。
            if (step == lastStep) {
                Spacer(Modifier.height(12.dp))
                NoticeCard(
                    title = stringResource(R.string.wiz_hint_title),
                    body = stringResource(R.string.wiz_hint_body)
                )
            }
        }

        // 按钮为什么是灰的必须写出来，否则用户只会觉得"卡住了"。
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
                // ⭐ 没拿到无障碍就点不动（见上方注释）。
                enabled = canAdvance
            ) { if (step < lastStep) step++ else onFinish() }
        }
    }
}

/**
 * 向导外壳：读状态、写偏好，由 [com.fucktmall.unlockassistant.SettingsActivity] 在
 * 「还没走完向导」时显示。[onFinish] 里**只退出，不打开天猫校园**。
 */
@Composable
fun WizardHost(resumeTick: Int, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val a11yOn = remember(resumeTick) { AppState.isAccessibilityEnabled(ctx) }

    WizardScreen(
        a11yOn = a11yOn,
        onOpenA11y = {
            // ⭐ 点「去开启」先弹一句 toast（文案 `wiz_s1_toast`）：按钮一点人就跳到系统设置页，
            // 本App 的教程卡留在后面看不见，而那一页最容易卡住人的是「服务列表在页面最底下」。
            // ⚠️ **必须先 show 再 startActivity**：跳走后本App 立刻退到后台，
            // Android 11+ 对后台 App 的 toast 是**直接丢弃**（不是排队），顺序反了就白写。
            Toast.makeText(ctx, R.string.wiz_s1_toast, Toast.LENGTH_LONG).show()
            MainActivity.openAccessibilitySettings(ctx)
        },
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
 * 卡里只放「是什么 / 现在什么状态 / 按钮」，**具体怎么点放卡外的 [StepsCard]** ——
 * 点完按钮就跳去系统设置了，教程卡留在屏幕上（回来时还在），卡里塞满步骤反而没人读。
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
