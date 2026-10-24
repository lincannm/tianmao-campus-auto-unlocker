package com.fucktmall.unlockassistant.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fucktmall.unlockassistant.AppState
import com.fucktmall.unlockassistant.R
import com.fucktmall.unlockassistant.Session
import com.fucktmall.unlockassistant.ShizukuA11y
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** 视频教程（用户给的 B 站短链，点「看视频教程」交给浏览器或 B 站 App）。 */
private const val VIDEO_TUTORIAL_URL = "https://b23.tv/te46sh3"

/**
 * 用户按下引导页那个动作按钮时写进日志的原文。
 *
 * 用常量而不是 `stringResource`：`Session.addLog` 在事件回调（非 composable 上下文）里调，
 * 而日志文本从来也不是给用户看的界面文案。
 */
private const val KEEP_ON_LOG = "用 Shizuku 保持无障碍开启（用户在引导页按过按钮）"

/** 路径图插图的宽高比（源图 1448×1086，由 `tools/make_devmode_guide_image.py` 产出）。 */
private const val DEVMODE_IMAGE_RATIO = 1448f / 1086f

/** 全屏看图的缩放上下限：1 = 适应屏幕；放到 6 倍足够看清图里最小的那行字。 */
private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 6f
private const val ZOOM_DOUBLE_TAP = 2.5f

/**
 * Shizuku 引导页（独立整页，入口是向导第 1 步与设置面板里那个按钮）。
 *
 * **为什么单独一页**：用这条路的人得先装 Shizuku、开「开发者模式」、启动它的服务 ——
 * 这些步骤写在向导/设置里既挤（向导首屏会被顶下去），又容易变成两份对不上的说法。
 * 于是那两个页面**只留一个按钮**，点进来才是这一页，而且这一页假定读者**完全没听说过 Shizuku**。
 *
 * 页面顺序：标题（页头**只有标题**）→ 一句话讲清这是干什么的（普通正文）→ 我现在到哪一步（状态胶囊 +
 * 一只动作按钮 + 常显的下载入口）→ 不装 Shizuku 的兜底 → 「第一次怎么装」（三步 + 开发者模式路径图 + 视频教程）。
 * ⭐ 最后那一整块**就绪时默认收起**：状态已经就绪（本页最常见的样子）时，那三步对用户是纯噪音；
 * 没就绪时默认摊开，因为那正是他当下要做的事。收起 / 展开随时可点。
 *
 * ⭐ **状态卡里只有一只动作按钮，而且文案固定**（「使用Shizuku开启无障碍」）：当前缺哪一步由状态胶囊表达，
 * 按钮不按状态改名；真走不下去时**按下之后才弹窗**说清是哪一种（服务没在跑 → [ShizukuOffDialog]，
 * 没授权 → Shizuku 自己的授权框）。都通了那一下就把那条一次性写回权限、立刻写一次、
 * 以及"以后每次打开本App 都自检写回"一起做掉。
 *
 * ⭐ **做完之后就没什么可做的了**：无障碍已经开着、自动维持也开着（[done]）时那只按钮**置灰**，
 * 胶囊改说「无障碍已开启，并会自动维持」，免得用户以为还得再按一次。
 *
 * 三条与设备有关的实现约束：
 * 1. **不检测 Shizuku 装没装**（[ShizukuA11y.state] 只问 binder 与授权）：包可见性查不准，
 *    查不到就把已装的人引去「再装一个」，比不判断更糟；所以下载直链在状态卡里**任何状态都显示**；
 * 2. 授权弹窗是 Shizuku 自己的界面、结果**异步**回来，所以状态挂在 [rememberShizukuStatus] 的结果监听上；
 * 3. 写回要起 Shizuku 远端进程，是 IO —— 走协程，结果用 toast 说、同时进日志（用户回来贴日志排查）。
 */
@Composable
fun ShizukuGuideScreen(resumeTick: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 本页自己的刷新计数：授权回来、刚写回一次、从 Shizuku / 系统设置回来，都要重读一遍。
    var tick by remember { mutableIntStateOf(0) }

    // 先把能力探测在 IO 线程预热一次（真起远端进程），下面组合期读状态时命中的就是缓存。
    LaunchedEffect(resumeTick, tick) {
        withContext(Dispatchers.IO) { ShizukuA11y.probeShellNow(force = true) }
    }

    val state = rememberShizukuStatus(ctx, resumeTick, tick)
    var canWrite by remember(resumeTick, tick) { mutableStateOf(ShizukuA11y.hasWriteSecureSettings(ctx)) }
    var zoomOpen by remember { mutableStateOf(false) }
    // 按下按钮却探不通（服务没在跑 / 没装）时弹一次，当面给一个启动入口。
    var shizukuOffDialog by remember { mutableStateOf(false) }

    // 这条路此刻能不能用：Shizuku 在跑就能借它的 shell 身份写；它没在跑、但本App 已经拿过
    // 那条一次性写回权限，也照样能写（第一档完全不经过 Shizuku，见 ShizukuA11y.ensureConnected）。
    val mechanismReady = state == ShizukuA11y.State.READY || canWrite

    // 「这条路已经做完」的样子：无障碍开着 + 自动维持也已开 + 当下确实维持得住。
    // 三者缺一都还值得让用户按一下（比如 Shizuku 没起来，按一下会去拉起它），所以按钮只在全满足时置灰。
    val a11yOn = remember(resumeTick, tick) { AppState.isAccessibilityEnabled(ctx) }
    val keepOn = remember(resumeTick, tick) { AppState.isShizukuKeep(ctx) }
    val done = a11yOn && keepOn && mechanismReady

    // 「第一次怎么装」那块开不开：null = 跟着状态走（就绪收成一行、没就绪摊开），
    // 用户点过一次之后就按他点的算。
    var howToOverride by remember { mutableStateOf<Boolean?>(null) }
    val showHowTo = howToOverride ?: !mechanismReady

    /**
     * 那一只按钮的动作：按一次走一步，缺哪一步就走哪一步。
     *
     * ① 服务没在跑（或根本没装）→ 弹 [ShizukuOffDialog]（里面一个「打开 Shizuku」，找不到启动入口就退回下载页）；
     * ② 服务在跑、但本App 还没被授权 → 弹 Shizuku 自己的授权框（结果异步回来，见 [rememberShizukuStatus]）；
     * ③ 都通了 → 先补上那条一次性写回权限（拿到之后写回不再要求 Shizuku 在跑），再把无障碍写回。
     *
     * ⚠️ **按钮文案固定，不许按状态改**：当前缺哪一步由上面的状态胶囊说，情况细节一律等按下之后再弹窗。
     * ⚠️ **这一下就是"用户显式开启"**：[AppState.setShizukuKeep] 在这里置位，没按过它时本App
     * 对系统设置只读（见 [ShizukuA11y] 的硬约束 1）。所以哪怕后面几步没走通也要先置位 ——
     * 用户的意思已经表达过了，服务一起来就该自己恢复。
     * ⚠️ 授权那一步**不能静默失败**：探不通时按 [ShizukuA11y.state] 分「服务没跑」与「没授权」，
     * 前者弹窗，后者弹授权框；补权限那一步失败则不拦路（写回还有借 shell 身份那条退路）。
     */
    val runOne: () -> Unit = {
        AppState.setShizukuKeep(ctx, true)
        Session.addLog(KEEP_ON_LOG)
        scope.launch {
            val ready = withContext(Dispatchers.IO) { ShizukuA11y.probeShellNow(force = true) }.isEmpty()
            if (!ready) {
                tick++
                if (ShizukuA11y.state(ctx) == ShizukuA11y.State.NO_PERMISSION) {
                    ShizukuA11y.requestPermission()
                } else {
                    shizukuOffDialog = true
                }
                return@launch
            }
            if (!canWrite) {
                withContext(Dispatchers.IO) { ShizukuA11y.grantWriteSecureSettings(ctx) }
            }
            val ok = withContext(Dispatchers.IO) { ShizukuA11y.ensureConnected(ctx) }
            canWrite = ShizukuA11y.hasWriteSecureSettings(ctx)
            Toast.makeText(
                ctx,
                if (ok) R.string.shz_done else R.string.shz_failed,
                Toast.LENGTH_LONG
            ).show()
            tick++
        }
    }

    Screen {
        // 页头**只有标题**：那句"这是干什么的"当普通正文摆在下面（页头副标题一行放不下长句）。
        ScreenHeader(title = stringResource(R.string.shzg_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            BodyText(stringResource(R.string.shzg_lead))
            Spacer(Modifier.height(14.dp))

            // 状态 + **一只文案固定**的按钮 + 常显的下载入口：胶囊说"现在什么情况 / 下一步做什么"，
            // 按钮永远叫「使用Shizuku开启无障碍」，走不下去时按下之后才弹窗解释（见 runOne）。
            StepCard {
                StatusChip(
                    // done 是"已经做完"那一态：胶囊不能再教用户点按钮，直接说现在的状态。
                    ok = mechanismReady,
                    okText = stringResource(
                        when {
                            done -> R.string.shz_state_done
                            state == ShizukuA11y.State.READY -> R.string.shz_state_ready
                            else -> R.string.shz_state_ready_no_shizuku
                        }
                    ),
                    badText = stringResource(
                        when (state) {
                            ShizukuA11y.State.NOT_RUNNING -> R.string.shz_state_not_running
                            ShizukuA11y.State.NO_PERMISSION -> R.string.shz_state_no_permission
                            ShizukuA11y.State.READY -> R.string.shz_state_ready
                        }
                    )
                )
                Spacer(Modifier.height(10.dp))

                // ⚠️ 这一只按钮的文案**不随状态变**（用户要求）：状态由上面的胶囊表达。
                // 已经开启并自动维持时置灰 —— 再点一次不会多出任何效果。
                PrimaryButton(
                    text = stringResource(R.string.shz_btn_one),
                    enabled = !done,
                    onClick = runOne
                )
                Spacer(Modifier.height(6.dp))
                HintText(
                    stringResource(
                        if (done) R.string.shz_keep_desc_done else R.string.shz_keep_desc
                    )
                )

                // 下载入口钉在状态卡最后一行、**任何状态都给**：本页不检测装没装
                // （包可见性查不准，见 ShizukuA11y.state 的注释），
                // 所以「装没装」这件事永远由用户自己看着这一行决定；已就绪时也不藏起来，
                // 免得 Shizuku 哪天被卸载后这一页变成死胡同。
                Spacer(Modifier.height(12.dp))
                OutlinedActionButton(
                    text = stringResource(R.string.shzg_btn_download),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!ShizukuA11y.openDownloadPage(ctx)) {
                        Toast.makeText(ctx, R.string.shz_download_failed, Toast.LENGTH_LONG).show()
                    }
                }
                Spacer(Modifier.height(6.dp))
                HintText(stringResource(R.string.shzg_download_hint))
            }

            // 兜底那条路摆在状态卡下面：它和上面那一只按钮是二选一，不该埋到页面最底下。
            Spacer(Modifier.height(14.dp))
            HintText(stringResource(R.string.shzg_no_shizuku))

            Spacer(Modifier.height(18.dp))

            HowToCard(
                expanded = showHowTo,
                onToggle = { howToOverride = !showHowTo },
                onZoom = { zoomOpen = true },
                onVideo = {
                    if (!ShizukuA11y.openUrl(ctx, VIDEO_TUTORIAL_URL)) {
                        Toast.makeText(ctx, R.string.shzg_video_failed, Toast.LENGTH_LONG).show()
                    }
                }
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    if (shizukuOffDialog) {
        ShizukuOffDialog(
            onOpenShizuku = {
                shizukuOffDialog = false
                if (!ShizukuA11y.openShizuku(ctx)) {
                    Toast.makeText(ctx, R.string.shz_download_failed, Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { shizukuOffDialog = false }
        )
    }

    if (zoomOpen) {
        ImageZoomOverlay(onClose = { zoomOpen = false })
    }
}

/** 卡内小标题（17sp SemiBold，与 `NoticeCard` / `StepsCard` 的标题一致）。字色按 [TintedCard] 的底色取。 */
@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * 「第一次怎么装」那一整块（一张可折叠的卡）：三步说明 → 开发者模式路径图 → 视频教程入口。
 *
 * ⭐ **就绪时默认收起**（展开态由调用方持有）：状态已经就绪时这些内容对用户是纯噪音，
 * 收成"一行标题 + 一句怎么展开"；没就绪时默认摊开，那正是他当下要做的事。
 *
 * 路径图夹在第 1 步里（第 1 步讲的就是"去哪点 7 次"），不再单列一张卡、也不写"见下面那张图"。
 * 宽高比写死，不让 painter 自己去"适配"（同 AboutScreen 三张插图踩过的坑）。
 */
@Composable
private fun HowToCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    onZoom: () -> Unit,
    onVideo: () -> Unit
) {
    TintedCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(
                text = stringResource(R.string.shzg_howto_title),
                modifier = Modifier.weight(1f)
            )
            // 箭头方向就是当前状态（收起时朝下 = 能往下展，展开时朝上 = 能收回去）。
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (!expanded) {
            Spacer(Modifier.height(8.dp))
            HintText(
                text = stringResource(R.string.shzg_howto_collapsed),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            return@TintedCard
        }

        Spacer(Modifier.height(14.dp))
        StepRow(
            index = 1,
            title = stringResource(R.string.shzg_step_dev_title),
            body = stringResource(R.string.shzg_step_dev_body),
            titleWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Image(
            painter = painterResource(R.drawable.il_devmode_paths),
            contentDescription = stringResource(R.string.shzg_img_desc),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DEVMODE_IMAGE_RATIO)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onZoom)
        )
        Spacer(Modifier.height(6.dp))
        HintText(
            text = stringResource(R.string.shzg_img_hint),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Spacer(Modifier.height(16.dp))
        StepRow(
            index = 2,
            title = stringResource(R.string.shzg_step_svc_title),
            body = stringResource(R.string.shzg_step_svc_body),
            titleWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        StepRow(
            index = 3,
            title = stringResource(R.string.shzg_step_back_title),
            body = stringResource(R.string.shzg_step_back_body),
            titleWeight = FontWeight.SemiBold
        )

        // 视频教程摆在三步之后：它是"看不懂再来"，不跟主按钮抢那一屏的注意力（所以是 outlined）。
        Spacer(Modifier.height(18.dp))
        CardTitle(stringResource(R.string.shzg_video_title))
        Spacer(Modifier.height(6.dp))
        BodyText(
            text = stringResource(R.string.shzg_video_body),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(10.dp))
        OutlinedActionButton(
            text = stringResource(R.string.shzg_video_btn),
            modifier = Modifier.fillMaxWidth()
        ) { onVideo() }

        Spacer(Modifier.height(14.dp))
        HintText(
            text = stringResource(R.string.shzg_steps_foot),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 全屏看图：黑底 + 双指缩放 / 拖动 / 双击放大还原，右上角一个「关闭」，系统返回键也能关。
 *
 * 拖动范围按当前缩放夹住（`clamp`）—— 不夹的话图很容易被拖到屏幕外面，用户以为"图没了"。
 */
@Composable
private fun ImageZoomOverlay(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(ZOOM_MIN) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        fun clamp(o: Offset, s: Float): Offset {
            val maxX = box.width * (s - 1f) / 2f
            val maxY = box.height * (s - 1f) / 2f
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        scale = next
                        offset = if (next <= ZOOM_MIN) Offset.Zero else clamp(offset + pan, next)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > ZOOM_MIN + 0.5f) ZOOM_MIN else ZOOM_DOUBLE_TAP
                            offset = Offset.Zero
                        }
                    )
                }
        ) {
            Image(
                painter = painterResource(R.drawable.il_devmode_paths),
                contentDescription = stringResource(R.string.shzg_img_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            Text(
                text = stringResource(R.string.shzg_zoom_hint),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(stringResource(R.string.shzg_zoom_close), color = Color.White)
            }
        }
    }
}

/**
 * Shizuku 的当前状态（服务在不在跑 / 有没有授权给本App）—— **不含「装没装」**，
 * 判定理由见 [ShizukuA11y.state]。
 *
 * 授权弹窗是 Shizuku 自己的界面、结果**异步**回来，所以这里挂一个结果监听：拿到结果就把
 * 内部 tick 加一，重新读一遍状态。外部自己的刷新时机（resume、刚做完动作）通过 [refreshKey]
 * 一起算进 `remember` 的 key 里 —— 不传就只在授权结果回来时刷新。
 */
@Composable
private fun rememberShizukuStatus(ctx: Context, vararg refreshKey: Any?): ShizukuA11y.State {
    var tick by remember { mutableIntStateOf(0) }
    val listener = remember { Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ } }
    DisposableEffect(Unit) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
    }
    return remember(tick, *refreshKey) { ShizukuA11y.state(ctx) }
}
