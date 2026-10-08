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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * 按下那个「用 Shizuku …」按钮时写进日志的原文。
 *
 * 用常量而不是 `stringResource`：`Session.addLog` 在事件回调（非 composable 上下文）里调，
 * 而日志文本从来也不是给用户看的界面文案。
 */
private const val KEEP_ON_LOG = "用 Shizuku 保持无障碍开启 = true"

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
 * 于是那两个页面**只留一个按钮**，点进来才是这一页，而且这一页假定读者**完全没听说过 Shizuku**：
 * 页面顺序就是操作顺序 —— 这是干什么的 → 我现在到哪一步（状态 + 按状态给一个按钮 + 下载入口）
 * → 视频教程 → 四步 → 开发者模式路径图（可点开全屏放大）→ 不装也能用的兜底。
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
    val a11yOn = remember(resumeTick, tick) { AppState.isAccessibilityEnabled(ctx) }
    var keep by remember(resumeTick, tick) { mutableStateOf(AppState.isShizukuKeep(ctx)) }
    var canWrite by remember(resumeTick, tick) { mutableStateOf(ShizukuA11y.hasWriteSecureSettings(ctx)) }
    var zoomOpen by remember { mutableStateOf(false) }

    // 「写一次并反馈结果」：状态卡里的按钮与开关都用它，行为只写一份。
    val runEnable: () -> Unit = {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ShizukuA11y.ensureEnabled(ctx) }
            Toast.makeText(
                ctx,
                if (ok) R.string.shz_done else R.string.shz_failed,
                Toast.LENGTH_LONG
            ).show()
            tick++
        }
    }

    // 一次性授权：让 Shizuku 替本App 授 WRITE_SECURE_SETTINGS，之后写回不再依赖 Shizuku 在跑。
    val runGrant: () -> Unit = {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ShizukuA11y.grantWriteSecureSettings(ctx) }
            Toast.makeText(
                ctx,
                if (ok) R.string.shz_perm_done else R.string.shz_perm_failed,
                Toast.LENGTH_LONG
            ).show()
            tick++
        }
    }

    /**
     * 那个「用 Shizuku …」按钮只有一个动作：**把这条保持机制打开，并且立刻写一次**。
     * 所以它不叫"开关"—— 点下去就有实际效果；不想再保持时用这一页的开关关掉。
     *
     * 服务没在跑 / 还没授权时也点它：先探一次实情，再按结果去弹授权框或提示，
     * 免得用户以为"这个按钮坏了"。
     */
    val runKeep: () -> Unit = {
        keep = true
        AppState.setShizukuKeep(ctx, true)
        Session.addLog(KEEP_ON_LOG)
        scope.launch {
            val ready = withContext(Dispatchers.IO) { ShizukuA11y.probeShellNow() }.isEmpty()
            if (!ready) {
                // 探不通：Shizuku 自己的授权框只能在界面上弹；没授权时它就是要弹的那一步。
                ShizukuA11y.requestPermission()
                Toast.makeText(ctx, R.string.shz_failed, Toast.LENGTH_LONG).show()
                tick++
                return@launch
            }
            // 通了而且无障碍本来就没开：这次点击直接把无障碍打开。
            if (!a11yOn) runEnable() else tick++
        }
    }

    Screen {
        ScreenHeader(title = stringResource(R.string.shzg_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            StepCard { BodyText(stringResource(R.string.shzg_lead)) }

            Spacer(Modifier.height(12.dp))

            // ⭐ 一次性授权卡：拿到之后本App 自己就能写回，**不再要求 Shizuku 当时在跑**。
            // 所以它排在这里而不是藏进设置页 —— 这是"让这条路不依赖 Shizuku 常驻"的关键一步。
            StepCard {
                StatusChip(
                    ok = canWrite,
                    okText = stringResource(R.string.shz_perm_ok),
                    badText = stringResource(R.string.shz_perm_missing)
                )
                if (!canWrite) {
                    Spacer(Modifier.height(8.dp))
                    HintText(stringResource(R.string.shz_perm_hint))
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = stringResource(R.string.shz_perm_btn),
                        onClick = runGrant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 状态 + 按状态给**一个**出口：小白只需要知道「我现在该点哪一下」。
            StepCard {
                StatusChip(
                    ok = state == ShizukuA11y.State.READY,
                    okText = stringResource(R.string.shz_state_ready),
                    badText = stringResource(
                        when (state) {
                            ShizukuA11y.State.NOT_RUNNING -> R.string.shz_state_not_running
                            ShizukuA11y.State.NO_PERMISSION -> R.string.shz_state_no_permission
                            ShizukuA11y.State.READY -> R.string.shz_state_ready
                        }
                    )
                )
                Spacer(Modifier.height(10.dp))

                // 那个「用 Shizuku …」按钮的文案跟着无障碍的真实状态走：
                // 无障碍没开时它要连"开启"一起包办，所以文案要说全。
                val keepBtnText = stringResource(
                    if (a11yOn) R.string.shz_btn_keep else R.string.shz_btn_keep_and_enable
                )

                when (state) {
                    ShizukuA11y.State.NOT_RUNNING -> {
                        HintText(stringResource(R.string.shz_hint_not_running))
                        Spacer(Modifier.height(10.dp))
                        OutlinedActionButton(
                            text = stringResource(R.string.shz_btn_open),
                            modifier = Modifier.fillMaxWidth()
                        ) { ShizukuA11y.openShizuku(ctx) }
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(text = keepBtnText, onClick = runKeep)
                    }

                    ShizukuA11y.State.NO_PERMISSION -> {
                        HintText(stringResource(R.string.shz_hint_no_permission))
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(text = stringResource(R.string.shz_btn_grant)) {
                            ShizukuA11y.requestPermission()
                        }
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(text = keepBtnText, onClick = runKeep)
                    }

                    ShizukuA11y.State.READY -> {
                        PrimaryButton(text = keepBtnText, onClick = runKeep)
                        Spacer(Modifier.height(6.dp))
                        HintText(stringResource(R.string.shz_keep_desc))
                    }
                }

                // 下载入口钉在状态卡最后一行、**任何状态都给**：本页不检测装没装
                // （包可见性查不准，见 ShizukuA11y.state 的注释），
                // 所以「装没装」这件事永远由用户自己看着这一行决定；已就绪时也不藏起来，
                // 免得 Shizuku 哪天被卸载后这一页变成死胡同。
                Spacer(Modifier.height(12.dp))

                // 关掉这条保持机制的唯一入口（上面那个按钮只会开启它）。
                // Shizuku 挂了（重启后没启动服务）时也要能关 —— 否则那个偏好就成了关不掉的隐形状态。
                if (keep) {
                    SwitchRow(
                        text = stringResource(R.string.shz_switch),
                        checked = keep,
                        onCheckedChange = { v ->
                            keep = v
                            AppState.setShizukuKeep(ctx, v)
                            Session.addLog("用 Shizuku 保持无障碍开启 = $v")
                        }
                    )
                    HintText(stringResource(R.string.shz_switch_desc))
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedActionButton(
                    text = stringResource(R.string.shzg_btn_download),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!ShizukuA11y.openDownloadPage(ctx)) {
                        Toast.makeText(ctx, R.string.shz_download_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 视频教程放在步骤前面：小白更愿意先看一遍演示，再照着做。
            StepCard {
                CardTitle(stringResource(R.string.shzg_video_title))
                Spacer(Modifier.height(8.dp))
                BodyText(stringResource(R.string.shzg_video_body))
                Spacer(Modifier.height(12.dp))
                PrimaryButton(text = stringResource(R.string.shzg_video_btn)) {
                    if (!ShizukuA11y.openUrl(ctx, VIDEO_TUTORIAL_URL)) {
                        Toast.makeText(ctx, R.string.shzg_video_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            StepsCard(
                title = stringResource(R.string.shzg_steps_title),
                steps = listOf(
                    stringResource(R.string.shzg_step1),
                    stringResource(R.string.shzg_step2),
                    stringResource(R.string.shzg_step3),
                    stringResource(R.string.shzg_step4)
                )
            )

            Spacer(Modifier.height(12.dp))

            // 第 2 步那张图：卡里按宽度铺满，点一下全屏放大（图里字多，不放大看不清）。
            // 宽高比写死，不让 painter 自己去"适配"（同 AboutScreen 三张插图踩过的坑）。
            StepCard {
                CardTitle(stringResource(R.string.shzg_img_title))
                Spacer(Modifier.height(10.dp))
                Image(
                    painter = painterResource(R.drawable.il_devmode_paths),
                    contentDescription = stringResource(R.string.shzg_img_desc),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(DEVMODE_IMAGE_RATIO)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { zoomOpen = true }
                )
                Spacer(Modifier.height(8.dp))
                HintText(stringResource(R.string.shzg_img_hint))
            }

            Spacer(Modifier.height(12.dp))
            HintText(stringResource(R.string.shzg_no_shizuku))
            Spacer(Modifier.height(20.dp))
        }
    }

    if (zoomOpen) {
        ImageZoomOverlay(onClose = { zoomOpen = false })
    }
}

/** 卡内小标题（17sp SemiBold，与 `NoticeCard` / `StepsCard` 的标题一致）。 */
@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
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
