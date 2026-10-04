package com.fucktmall.unlockassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fucktmall.unlockassistant.R

/**
 * 界面零件。全部按 Material Design 3 写：
 *  - 文字用 MD3 的字号/行高（titleLarge 22、headlineSmall 24、bodyLarge 16）；
 *  - 按钮是 MD3 的 40dp+ 高度、全圆角（pill）；
 *  - 卡片容器用 surfaceContainerLow，对话框用 MD3 的 AlertDialog（28dp 圆角）。
 *
 * 边到边：inset 交给 [Screen] 里的 windowInsetsPadding(WindowInsets.safeDrawing)，
 * 取代原来手写的那套 Ui.applyInsets。
 *
 * 主题色是红色（见 [UnlockTheme]）。
 */

@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            content = content
        )
    }
}

/**
 * 页头：**Material 3 的 [TopAppBar]**（用户 3.3 明确要求用 MD3 组件，别再自画）。
 *
 * [onBack] 给了就在最左边出一个返回按钮（`navigationIcon` + `IconButton` + 官方 `arrow_back` 矢量）——
 * 只有独立整页需要（设置页是长按图标直达的顶层页，没有上一级）。
 *
 * ⚠️ `TopAppBar` 在 material3 里是 `@ExperimentalMaterial3Api` 的，所以要 [OptIn]
 * （它同时给了返回按钮 48dp 的触摸目标、正确的标题排版和 M3 配色，比自己摆一行文字划算）。
 *
 * ⚠️ **`windowInsets` 必须归零**（`WindowInsets(0)`）：本 App 的外层 [Screen] 已经用
 * `windowInsetsPadding(WindowInsets.safeDrawing)` 统一处理了状态栏/手势条，
 * [TopAppBar] 默认还会再加一份状态栏高度，那样标题会被顶下去一截。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    TopAppBar(
        // 标题下面的副标题：M3 的 TopAppBar 没有独立副标题位，塞进 title 里两行。
        title = {
            Column {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.nav_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        windowInsets = WindowInsets(0)
    )
}

/** 进度条：比「第 1 步 / 共 3 步」这行字更快看懂自己在哪。 */
@Composable
fun StepBars(total: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until total) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (i <= current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            if (i < total - 1) Spacer(Modifier.width(6.dp))
        }
    }
}

/** MD3 卡片容器（filled card 的等价物）：放正文内容。 */
@Composable
fun StepCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

/**
 * 需要用户注意的提示卡（主题色底，和普通内容卡明显分开）。
 *
 * 存在的理由：向导里「以后想改设置」原来只是正文下面一行灰色小字，
 * 用户反馈太不明显 —— 而这句话恰恰是本次向导要教会用户的事（设置在哪）。
 * 所以给它一张自己的卡 + 主题色底 + 加粗标题。
 */
@Composable
fun NoticeCard(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** MD3 headlineSmall：一步的标题，一眼一个词。 */
@Composable
fun StepTitle(text: String) {
    Text(
        text = text,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** MD3 bodyLarge。文案里自己带 \n，一行一个意思，避免一大段要读。 */
@Composable
fun BodyText(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** MD3 bodySmall / labelMedium：补充说明，弱一级。 */
@Composable
fun HintText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 状态胶囊。已达成 = **浅红底 + 深红字**（primaryContainer / onPrimaryContainer），
 * 没达成 = **实心深红 + 白字**（error / onError）。
 *
 * ⭐ 为什么两只都用红、却不会混：本主题里 `primaryContainer` 与 `errorContainer`
 * 的值**是同一个** `#FFDAD6`（同一套 MD3 红色调色板的 tone90），所以「已达成」以前
 * 才被挪去用绿、后来又改用暖琥珀金 `tertiaryContainer`。用户最新一轮反馈：琥珀金
 * **不在红色家族里、跟主题色不搭** —— 于是改成「同色系、分深浅」：
 * 已达成是浅红容器（软），没达成是实心 `error` 红（硬）。
 * 状态仍然一眼可分，而整页**只剩红一支色系**。
 *
 * 注意「没达成」必须跟着一起用 `error` 实心 —— 如果继续用 `errorContainer`，
 * 两只胶囊会变成一模一样的 `#FFDAD6`，等于没有状态区分。
 * 取色一律走色板，不写死常量，改主题时会跟着变。
 */
@Composable
fun StatusChip(ok: Boolean, okText: String, badText: String) {
    val bg = if (ok) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.error
    }
    val fg = if (ok) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onError
    }
    val prefix = if (ok) "✓  " else "✗  "
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            text = prefix + if (ok) okText else badText,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

/** MD3 filled button。 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(50)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** MD3 outlined button。 */
@Composable
fun OutlinedActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(50)
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 设置项行：左边说明，右边 MD3 Switch。 */
@Composable
fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 无障碍关闭时的提示。
 *
 * 存在的理由：无障碍没开，天猫校园的开屏广告就没人点掉，用户会看到
 * 「本该自动关掉却还停在屏幕上的广告」而不知道发生了什么。
 * 所以默认**不打开**，先把话说清楚；想硬进也留了「仍要打开」，不把人堵死。
 */
@Composable
fun A11yOffDialog(
    onOpenSettings: () -> Unit,
    onOpenAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_a11y_title)) },
        text = {
            Text(
                text = stringResource(R.string.dlg_a11y_body),
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.dlg_a11y_go)) }
        },
        dismissButton = {
            TextButton(onClick = onOpenAnyway) { Text(stringResource(R.string.dlg_a11y_anyway)) }
        }
    )
}

/** 没装天猫校园时的提示。 */
@Composable
fun NoTmallDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_notmall_title)) },
        text = {
            Text(
                text = stringResource(R.string.dlg_notmall_body),
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_ok)) }
        }
    )
}

/**
 * 「了解本App」原来是个 AlertDialog（[AboutDialog]，3.0 起），
 * **3.3 起改成独立整页**（[AboutScreen] + `AboutActivity`）——用户要求「不要弹窗，跳转到新的 activity」。
 * 弹窗放不下插图和三段说明，而且它是盖在设置页上的，看上去像临时提示；
 * 整页有返回键、能滚动、还能放下三张插图。
 *
 * 版本号从 PackageManager 现读，不引 BuildConfig ——
 * 这样不用为了一个字符串去开 `buildFeatures.buildConfig`，
 * 也不会出现「代码里的版本号和实际装的包不一致」。读不到就显示 "?"。
 */
@Suppress("DEPRECATION")
@Composable
fun rememberAppVersion(): Pair<String, Int> {
    val ctx = LocalContext.current
    return remember {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (pi.versionName ?: "?") to pi.versionCode
        } catch (t: Throwable) {
            "?" to 0
        }
    }
}

/** 两列 outlined 按钮：MD3 里按钮不贴边，中间留 10dp。 */
@Composable
fun OutlinedButtonPair(
    leftText: String,
    rightText: String,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    // 注意必须有 fillMaxWidth：wrap_content 的 Row 里 weight 拿不到可分配宽度，会量成 0。
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedActionButton(text = leftText, modifier = Modifier.weight(1f), onClick = onLeft)
        OutlinedActionButton(text = rightText, modifier = Modifier.weight(1f), onClick = onRight)
    }
}
