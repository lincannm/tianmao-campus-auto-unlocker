package com.fucktmall.unlockassistant.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fucktmall.unlockassistant.R

/** 界面零件，按 Material Design 3 写：MD3 字号/行高、40dp+ 全圆角按钮、
 *  surfaceContainerLow 卡片、AlertDialog 对话框。
 *  边到边 inset 交给 [Screen] 的 `windowInsetsPadding(WindowInsets.safeDrawing)`；主题色见 [UnlockTheme]。 */

/**
 * [TroubleshootCard] 那张插图的尺寸：**宽度写死 156dp**，高度按 [TROUBLE_IMAGE_RATIO] 推。
 *
 * 为什么压这么小：要让收起的卡尽量短（这张图在折叠时也显示），156dp 时高约 107dp，
 * 图里的标题与那行蓝字仍看得清。**比例给死**是为了不让 `painter` 自己去"适配"
 * （同 [AboutScreen] 里三张插图踩过的坑）。
 * 图本身由 `tools/make_a11y_denied_image.py` 裁好缩好放进 `drawable-nodpi/`，产物 480×329。
 */
private val TROUBLE_IMAGE_WIDTH = 156.dp
private const val TROUBLE_IMAGE_RATIO = 480f / 329f

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
 * 页头：Material 3 的 [TopAppBar]（`@ExperimentalMaterial3Api`，所以要 [OptIn]）。
 * [onBack] 给了就在最左边出一个返回按钮（`navigationIcon` + `IconButton` + 官方 `arrow_back` 矢量），
 * 只有独立整页需要。
 *
 * ⚠️ **`windowInsets` 必须归零**（`WindowInsets(0)`）：外层 [Screen] 已经用
 * `windowInsetsPadding(WindowInsets.safeDrawing)` 统一处理了状态栏/手势条，
 * [TopAppBar] 默认还会再加一份状态栏高度，标题会被顶下去一截。
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

/** 进度条：一眼看出自己在第几步。 */
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

/** 需要用户注意的提示卡：主题色底 + 加粗标题，和普通内容卡明显分开；向导里用它讲「以后想改设置」。 */
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

/**
 * 编号步骤卡：教用户「在系统设置里怎么点」。与 [NoticeCard] 同色系，但一眼看得出是"照做"的清单。
 *
 * 为什么需要：向导第 1 步要用户去系统设置开无障碍，而那一页**不在本App 里** ——
 * 用户点完就跳走，回来时全靠记忆；那一页可能叫「无障碍」也可能叫「辅助功能」，
 * 服务列表还藏在「已下载的应用 / 已安装的服务」里，只给一句话等于让人自己找。
 * ⚠️ 正文必须用**跨品牌的通用说法**：不能把某一个品牌的路径当成所有人的路径。
 *
 * 序号圆点是 `onPrimaryContainer` 实心底 + `primaryContainer` 字色 —— 在主题色上挖空，
 * 不需要额外的语义色（本主题里可用色不多，见 [StatusChip] 的说明）。
 */
@Composable
fun StepsCard(
    title: String,
    steps: List<String>,
    modifier: Modifier = Modifier
) {
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
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { i, step ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${i + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = step,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 可折叠的排查卡：讲「照正常步骤做却被系统拦住了」时怎么办。结构与 [StepsCard] 相同（标题 + 一句正文 + 编号清单 + 一句小字），
 * 但**默认只露标题 + 插图**，整张卡可点着展开 / 收起（展开状态由调用方持有，见 [WizardScreen]）。
 *
 * ⭐ **插图放在标题下面、折叠时也照常显示**：只写文字时，用户得先在脑子里把"我看到的那个弹窗"和卡上的
 * 描述对上号才敢展开；把截图放在标题下，认不认得出一眼就知道，所以它比正文更该常驻。
 * 代价是收起的卡也有一张图的高度，这也是把图压到 156dp 的原因。
 *
 * 用 `tertiaryContainer`（暖琥珀金）而不是 `primaryContainer`：向导里这张卡是**另一个问题**的答案，
 * 与那张「怎么开启」的正常步骤卡必须一眼分得开 —— 两张同色的话，用户会把排查步骤当成正常步骤接着做。
 * 主题里 `tertiary` 这支色只给这张卡用（见 [UnlockTheme]）。
 *
 * 折叠是内容量的取舍：这张卡跟全量步骤卡一样长，但**大多数用户根本不会碰到那个弹窗**；
 * 直接摊开会把「怎么开启」挤到屏幕下面，反而挡住真正每个人都要走的那几步。
 * 标题下面跟着一行「点开看怎么处理」—— 只给箭头的话要靠用户猜，而这行字本身就是"点这里"的提示。
 *
 * 正文里的让步：**只写"弹窗自己怎么说就怎么点"**。各品牌/各版本在这一处的入口名与路径都不一样，
 * 把某一家（或某个 Android 版本的官方路径）写死会让其他机型的用户按错地方，所以这里不抄任何一条具体路径。
 */
@Composable
fun TroubleshootCard(
    title: String,
    body: String,
    steps: List<String>,
    hint: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    collapsedHint: String,
    imageDesc: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onToggle
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.width(8.dp))
                // 箭头方向就是当前状态（收起时朝下 = 能往下展，展开时朝上 = 能收回去）。
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(Modifier.height(10.dp))
            Image(
                painter = painterResource(R.drawable.il_a11y_denied),
                // 截图里就是弹窗原文，读屏用户听这一句就够，不用把图里的字再念一遍。
                contentDescription = imageDesc,
                modifier = Modifier
                    .width(TROUBLE_IMAGE_WIDTH)
                    .height(TROUBLE_IMAGE_WIDTH / TROUBLE_IMAGE_RATIO)
                    // 截图自带圆角，再裁一道把裁切框那点边也切干净。
                    .clip(RoundedCornerShape(14.dp))
            )

            if (!expanded) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = collapsedHint,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { i, step ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${i + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = step,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = hint,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
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
 * ⭐ **两只必须一起改**：本主题里 `primaryContainer` 与 `errorContainer` 的值**是同一个**
 * `#FFDAD6`（同一套 MD3 红色调色板的 tone90），所以「没达成」不能再用 `errorContainer`
 * —— 那样两只胶囊会一模一样，等于没有状态区分。取色一律走色板，不写死常量。
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
 * 版本号：从 PackageManager 现读，**不引 BuildConfig** —— 这样不用为了一个字符串去开
 * `buildFeatures.buildConfig`，也不会出现「代码里的版本号和实际装的包不一致」。
 * 读不到就显示 "?"。给 [AboutScreen] 的版本行用。
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

