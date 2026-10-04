package com.fucktmall.unlockassistant.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fucktmall.unlockassistant.AboutActivity
import com.fucktmall.unlockassistant.AppState
import com.fucktmall.unlockassistant.FireResult
import com.fucktmall.unlockassistant.MainActivity
import com.fucktmall.unlockassistant.R
import com.fucktmall.unlockassistant.Session
import com.fucktmall.unlockassistant.UnlockAccessibilityService
import kotlinx.coroutines.delay

/**
 * 设置 / 控制面板（MD3，3.0 起主题是红色）。
 *
 * 入口是**长按桌面图标 → 设置**（静态快捷方式），以及 deep link 抛失败时的落地页。
 *
 * 面板每秒刷一次：无障碍状态、自动跳过剩余时间、累计点掉的弹窗数、动作日志。
 * 日志每行带 `HH:mm:ss` 时间戳（见 `Session.addLog`）。
 *
 * 3.0 的三点改动（都是用户要求）：
 *  - 顶部状态卡**去掉了原来那块为「服务还没连上」预留的空白高度**，并在卡内底部
 *    加了「了解本App」（3.3 起它跳到独立整页 [AboutActivity]，不再是弹窗）；
 *  - 去掉了「自检：假广告」按钮和它那个假弹窗（[SelfTestDialog] 已删）；
 *  - 去掉了「系统清掉授权后自动恢复」开关 —— 那个功能整个撤了（见 `AppState` 的说明），
 *    本App 现在**只读**系统设置，不再申请 `WRITE_SECURE_SETTINGS`。
 *
 * 3.2 的三点改动（都是用户要求）：删掉「打开本App自动跳到开锁界面」开关（自动跳转从此固定常开）、
 * 两个代按开关换序（打开 App 在前、小部件在后）、日志每行加时间戳。
 *
 * 3.3：卡里那个「了解本App」**从弹窗改成 startActivity 跳新页**（用户要求「不要弹窗」）。
 */
@Composable
fun SettingsScreen(
    resumeTick: Int,
    onOpenA11y: () -> Unit,
    onOpenWizard: () -> Unit
) {
    val ctx = LocalContext.current

    // 每秒重算一次，让「自动跳过剩余秒数」和日志自己往前走。
    var now by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now++
        }
    }

    val a11yOn = remember(resumeTick, now) { AppState.isAccessibilityEnabled(ctx) }
    val serviceConnected = remember(resumeTick, now) { AppState.isServiceConnected() }
    val remainingMs = remember(resumeTick, now) { Session.remainingMs(ctx) }
    val logs = remember(resumeTick, now) { Session.snapshot() }
    // 两个「代按开锁」开关（3.1）：界面上「打开 App」在前、「小部件」在后（3.2 用户要求换序）。
    var unlockOnApp by remember(resumeTick) { mutableStateOf(AppState.isUnlockOnApp(ctx)) }
    var unlockOnWidget by remember(resumeTick) { mutableStateOf(AppState.isUnlockOnWidget(ctx)) }

    var a11yPrompt by remember { mutableStateOf(false) }

    Screen {
        ScreenHeader(title = stringResource(R.string.set_title))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 这块卡**不再钉最小高度**：原来为了防抖钉了 188dp，卡里就多出一截空白（用户指出）。
            // 现在卡多高就多高，最下面一行放「了解本App」。
            StepCard {
                StatusChip(
                    ok = a11yOn,
                    okText = stringResource(R.string.set_a11y_on),
                    badText = stringResource(R.string.set_a11y_off)
                )
                // 设置里开着、服务却没连上：说明服务被系统杀了还没绑回来，
                // 这时候弹窗其实也不会被点掉（区分「用户关了」和「系统杀了」两种失效）。
                if (a11yOn && !serviceConnected) {
                    Spacer(Modifier.height(6.dp))
                    HintText(stringResource(R.string.set_a11y_no_bind))
                }
                Spacer(Modifier.height(12.dp))
                HintText(
                    if (remainingMs > 0) {
                        stringResource(R.string.set_auto_active, (remainingMs / 1000L).toInt())
                    } else {
                        stringResource(R.string.set_auto_none)
                    }
                )
                Spacer(Modifier.height(4.dp))
                HintText(stringResource(R.string.set_click_count, UnlockAccessibilityService.clickCount))

                Spacer(Modifier.height(8.dp))
                // 左对齐的纯文字按钮：contentPadding 归零才不会被按钮自带的内边距顶歪。
                // 3.3：点它**跳到独立整页**（原来是弹 AboutDialog 盖在当前页上，用户要求改掉）。
                TextButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(ctx, AboutActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.set_btn_about),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            StepCard {
                PrimaryButton(text = stringResource(R.string.set_btn_open)) {
                    // 无障碍没开时不再照旧打开：先弹提示，让用户知道广告会留下来。
                    // 代按与否跟着「打开 App 时」那个开关走 —— 这个按钮就是待在 App 里的入口。
                    if (MainActivity.tryFireUnlock(
                            ctx,
                            "手动",
                            armUnlock = AppState.isUnlockOnApp(ctx)
                        ) == FireResult.A11Y_OFF
                    ) {
                        a11yPrompt = true
                    }
                }
                Spacer(Modifier.height(14.dp))
                // 打开 App 入口（3.2 起排在前面）
                SwitchRow(
                    text = stringResource(R.string.set_unlock_app),
                    checked = unlockOnApp,
                    onCheckedChange = { v ->
                        unlockOnApp = v
                        AppState.setUnlockOnApp(ctx, v)
                        Session.addLog("打开App代按开锁 = $v")
                    }
                )
                HintText(stringResource(R.string.set_unlock_app_desc))
                Spacer(Modifier.height(10.dp))
                // 小部件入口
                SwitchRow(
                    text = stringResource(R.string.set_unlock_widget),
                    checked = unlockOnWidget,
                    onCheckedChange = { v ->
                        unlockOnWidget = v
                        AppState.setUnlockOnWidget(ctx, v)
                        Session.addLog("小部件代按开锁 = $v")
                    }
                )
                HintText(stringResource(R.string.set_unlock_widget_desc))
            }

            Spacer(Modifier.height(12.dp))

            StepCard {
                OutlinedButtonPair(
                    leftText = stringResource(R.string.set_btn_stop),
                    rightText = stringResource(R.string.set_btn_a11y),
                    onLeft = {
                        Session.stop(ctx)
                        Session.addLog("已手动停止自动跳过")
                    },
                    onRight = onOpenA11y
                )
                Spacer(Modifier.height(10.dp))
                OutlinedActionButton(
                    text = stringResource(R.string.set_btn_wizard),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenWizard
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.log_title),
                modifier = Modifier.padding(bottom = 6.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 日志区**和上面的卡同色**（surfaceContainerLow）。
            // 原来用的是 surfaceContainerHighest `#F1DFDC`，比卡片（`#FFF1EE`）深一档，
            // 用户看真机后指出「日志卡片比别的颜色深」—— 日志是正文的一部分，不该是凹下去的块。
            // 不用 surfaceContainerLowest：深色主题里它是最黑的一档（`#140C0B`），会反过来更突兀。
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = if (logs.isEmpty()) {
                        stringResource(R.string.log_empty)
                    } else {
                        logs.joinToString("\n")
                    },
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // 从无障碍设置页回来、并且已经打开了：提示自己消失（`a11yOn` 刷新后为 true）。
    if (a11yPrompt && !a11yOn) {
        A11yOffDialog(
            onOpenSettings = onOpenA11y,
            onOpenAnyway = {
                a11yPrompt = false
                MainActivity.tryFireUnlock(ctx, "仍要打开", ignoreA11y = true)
            },
            onDismiss = { a11yPrompt = false }
        )
    }

    // 「了解本App」3.3 起是独立 Activity（AboutActivity），这里不再有弹窗要收。
}
