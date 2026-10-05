package com.fucktmall.unlockassistant.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fucktmall.unlockassistant.R

/**
 * 「了解本App」整页（独立 Activity，不是盖在设置页上的弹窗）。
 * 页面顺序，**不要随手调整**：三张插图**水平居中**（本App 图标 + 天猫校园图 + 会动的吉祥物）
 * → 版本号（**在三张图正下方**，不是页尾）→ 「开源代码」卡（整块可点）
 * → 「Vibed by: DeepSeek V4.1 Flash」致谢卡（**紧跟在开源代码卡下面**）→ 无标题正文卡
 * → 怎么用 → 如何进入设置界面。顶栏是 **Material 3 `TopAppBar`**。
 * 图形资源全部出自 `tools/export_about_images.py`，**不要在 res 里手改**。
 */

/** 插图行高度：三张图都用它当高度，宽度按各自长宽比推出来。 */
private val ILLUSTRATION_HEIGHT = 96.dp

/**
 * 三张图各自的**长宽比**（宽/高），宽度一律按 `高度 × 比例` 算出来给死：
 * `ContentScale.Fit` 拿到的是 `AnimatedImageDrawable` 自己报的固有尺寸，跟真实比例对不上，
 * 会被带偏成横向拉伸。
 */
private const val ICON_RATIO = 192f / 192f      // 本App 图标（正方形）
private const val TMALL_RATIO = 255f / 238f     // 255×238
private const val MASCOT_RATIO = 320f / 426f    // 320×426

private val ICON_WIDTH = ILLUSTRATION_HEIGHT * ICON_RATIO
private val TMALL_WIDTH = ILLUSTRATION_HEIGHT * TMALL_RATIO
private val MASCOT_WIDTH = ILLUSTRATION_HEIGHT * MASCOT_RATIO

private const val REPO_URL = "https://github.com/lincannm/tianmao-campus-auto-unlocker"
private const val TAG = "AboutScreen"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    // 版本号读一次就够；读法见 Components.kt 的 rememberAppVersion。
    val version = rememberAppVersion()

    // 动画插图：解不出来就是 null，那时只显示那两张静态图，页面照样完整。
    val mascot: Drawable? = rememberMascot()

    Screen {
        ScreenHeader(
            title = stringResource(R.string.about_title),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 三张插图：**水平居中**交给外层 Box 的 `contentAlignment`，不依赖
            // 「这个 Row 有多宽」的推断（外层撑满可用宽度，内层 `Row` 只包三张图）。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Illustration(R.drawable.il_about_icon, ICON_WIDTH)
                    Spacer(Modifier.width(10.dp))
                    Illustration(R.drawable.il_about_tmall, TMALL_WIDTH)
                    Spacer(Modifier.width(10.dp))
                    // 满幅矩形图，套一层圆角跟卡片对齐（图标与天猫图自带透明边，不用套）。
                    if (mascot != null) {
                        AnimatedImage(
                            drawable = mascot,
                            modifier = Modifier
                                .height(ILLUSTRATION_HEIGHT)
                                .width(MASCOT_WIDTH)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            // 版本号：紧贴三张插图下方。
            Text(
                text = stringResource(R.string.about_version, version.first, version.second),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RepoCard(onClick = { openRepo(ctx) })

            Spacer(Modifier.height(12.dp))

            // 致谢卡：**紧跟在「开源代码」卡下面**，别挪到页尾。
            VibedCard()

            Spacer(Modifier.height(12.dp))

            // 这段正文**不带标题**。
            StepCard {
                BodyText(stringResource(R.string.about_what_body))
            }

            Spacer(Modifier.height(12.dp))

            // 两种用法。
            TitledCard(stringResource(R.string.about_how_title)) {
                BodyText(stringResource(R.string.about_how_body))
            }

            Spacer(Modifier.height(12.dp))

            // 设置在哪。
            TitledCard(stringResource(R.string.about_where_title)) {
                BodyText(stringResource(R.string.about_where_body))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * 「Vibed by」卡：图标在左 + 一行字，**位置在「开源代码」卡正下方**（不是页尾）。
 * 图标是 `ic_stars.xml`，由 `tools/export_about_images.py` 原样搬运。
 */
@Composable
private fun VibedCard() {
    StepCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_stars),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.about_vibed),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 解码插图 → 优先拿"会动的那个"，失败再逐级退（见 `MascotAnimation.kt`）。
 */
@Composable
private fun rememberMascot(): Drawable? {
    val ctx = LocalContext.current
    return remember { loadMascot(ctx) }
}

/** 一张静态插图：高度固定 [ILLUSTRATION_HEIGHT]，宽度由调用方按长宽比算好传进来。 */
@Composable
private fun Illustration(resId: Int, width: Dp) {
    Image(
        painter = painterResource(resId),
        // 插图是纯装饰，语义由正文承担。
        contentDescription = null,
        modifier = Modifier
            .height(ILLUSTRATION_HEIGHT)
            .width(width),
        // 尺寸已按真实长宽比给死，不需要再让 painter 去"适配"。
        contentScale = ContentScale.FillBounds
    )
}

/**
 * 会动的插图：**自己驱动帧**，不指望 drawable 自己会走。
 *
 * ⭐ `AnimatedImageDrawable`（API 28+ 解动画 WebP/GIF 得到的那个）靠**宿主注册的回调**
 * 决定何时推进到下一帧；`View` 体系里 `setCallback(view)` 帮你做了，而 Compose 里我们只是
 * 把 drawable 画到画布上，**没有任何人给它回调** —— 不自己驱动它永远停在第一帧。
 * 所以显式 `start()`，再用 `withFrameNanos` 每帧重画逼它按时间推进（循环条件是 `isRunning`）。
 * ✅ 必须先 `start()` 再看 `isRunning`：首次组合时它还是 false，当循环条件会一次都不进。
 *
 * 这里**不用** `ContentScale`：宽高由调用方按真实长宽比给死，直接铺满即可。
 */
@Composable
private fun AnimatedImage(drawable: Drawable, modifier: Modifier = Modifier) {
    LaunchedEffect(drawable) {
        // ⚠️ 先 start() 再看 isRunning（原因见上）。
        val animatable = drawable as? Animatable
        animatable?.start()
        while (animatable?.isRunning == true) {
            withFrameNanos { }
        }
    }
    Canvas(modifier = modifier) {
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        // 必须是**原生** Canvas：`Drawable.draw` 收的是 android.graphics.Canvas，
        // 而 DrawScope 的 `canvas` 是 Compose 的 Canvas 包装。
        drawable.draw(drawContext.canvas.nativeCanvas)
    }
}

/**
 * 「开源代码」卡：GitHub 标 + `owner/repo` + 「点击访问 GitHub 项目」+ 末尾箭头，整卡可点。
 * 那句文案和 `chevron_right` 箭头都是为了让人看得出能点。
 * 用 `primaryContainer` 底（浅红）：和近白的正文卡明显分开，要的是「像名片」。
 */
@Composable
private fun RepoCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                // 单色矢量按主题色上色：浅红底上是深红，深色主题下自动换浅色。
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_repo_title),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                // 仓库名用等宽字：一眼看出是「owner/repo」这种可以照抄的路径。
                Text(
                    text = stringResource(R.string.about_repo_path),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_repo_open),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            // 「点得进去」的惯用提示。自画两条线，免得为它再引 material-icons-extended。
            Image(
                painter = rememberVectorPainter(chevronRight()),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
            )
        }
    }
}

/**
 * 右箭头（`chevron_right` 的几何：两条 45° 线）。用 [PathBuilder] 画 ——
 * 它的方法名是后缀式的（相对 = `moveToRelative`/`lineToRelative`），跟 `Path` 那套不一样。
 */
private fun chevronRight(): ImageVector {
    val path = PathBuilder().apply {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }
    return ImageVector.Builder(
        name = "chevron_right",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = path.nodes,
            fill = null,
            stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

/** 带标题的正文卡（标题 17sp 半粗 + 正文 bodyLarge），与设置页正文卡同色。 */
@Composable
private fun TitledCard(title: String, content: @Composable () -> Unit) {
    StepCard {
        Text(
            text = title,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/**
 * 点卡片 → 交给系统浏览器打开。
 *
 * 为什么要 `INTERNET` 权限：Android 规定 **App 自己**得有 INTERNET 才有资格把 http(s)
 * 链接交给别的应用（否则在 resolve 阶段就被拒）。本App **没有任何联网代码**，
 * 这条权限的唯一用途就是这个跳转。
 *
 * 打不开就 toast 说清楚，不静默失败 —— 页面上那行 `owner/repo` 仍然可以照着手打。
 */
private fun openRepo(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (t: Throwable) {
        // ActivityNotFoundException 是预期内的，其他 Throwable 也一并兜住，别让这一下点击崩掉整页。
        Log.w(TAG, "打开仓库链接失败（${t.javaClass.simpleName}）：${t.message}")
        Toast.makeText(ctx, ctx.getString(R.string.about_repo_failed), Toast.LENGTH_LONG).show()
    }
}
