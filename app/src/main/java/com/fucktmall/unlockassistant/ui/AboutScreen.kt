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
 * 「了解本App」整页（3.3 起是独立 Activity，不再是盖在设置页上的弹窗）。
 *
 * 页面顺序（用户 3.3 定稿，**不要随手调整**）：
 *
 *   1. 三张插图**水平居中**：本App 图标 + 用户给的两张图；
 *   2. 版本号（**用户点名要放在三张图的正下方**，不是页尾）；
 *   3. 「开源代码」卡：GitHub 标 + `owner/repo` + 「点击访问 GitHub 项目」，整块可点；
 *   4. 「Vibed by: DeepSeek V4.1 Flash」致谢卡（**紧跟在开源代码卡下面**，用户点名的位置）；
 *   5. 无标题正文卡（无障碍那段）；
 *   6. 怎么用（自动开锁 / 仅提前打开 两种方式）；
 *   7. 如何进入设置界面。
 *
 * 顶栏是 **Material 3 `TopAppBar`**（用户 3.3 要求用 MD3 组件，别再自画顶栏）：
 * 返回键 = `navigationIcon`，用 Material 官方 `arrow_back` 矢量（`ic_arrow_back.xml`）。
 *
 * 图形资源全部出自 `tools/export_about_images.py`，**不要在 res 里手改**：
 *  - `il_about_icon.png` / `il_about_tmall.png`（`drawable-nodpi`）；
 *  - `il_about_mascot.webp` + `il_about_mascot_still.png`（**`res/raw/`** —— 放 `assets/` 会
 *    在运行时 FileNotFoundException，工程里没有 assets 源集，第一版就是这么翻车的）；
 *  - `ic_github.xml`（pathData 逐字取自用户给的 `github (1).svg`，手抄的那版图形是乱的）、
 *    `ic_arrow_back.xml`（Material 官方）。
 *
 * 动画 WebP 的兼容策略：`BitmapFactory.decodeStream` 在 API 28+ 返回 `AnimatedImageDrawable`
 * （自己循环播），API 24–27 返回 `Bitmap`（只有第一帧）—— 一个调用覆盖两种机型，不用分支；
 * 真解不出来还有 `il_about_mascot_still.png` 兜底。
 */

/** 插图行高度：三张图都用它当高度，宽度按各自长宽比推出来。 */
private val ILLUSTRATION_HEIGHT = 96.dp

/**
 * 三张图各自的**长宽比**（宽/高）。宽度一律按 `高度 × 比例` 算出来给死，
 * 这样即使某台设备把动画 WebP 解成了"尺寸不同的 drawable"，也不会被拉扁
 * （用户 3.3 反馈：GIF 被横向拉伸了一点 —— 原因是 `ContentScale.Fit` 拿到的是
 *  `AnimatedImageDrawable` 自己报的固有尺寸，跟位的真实比例对不上）。
 */
private const val ICON_RATIO = 192f / 192f      // 本App 图标（正方形）
private const val TMALL_RATIO = 255f / 238f     // 255×238 ≈ 1.071
private const val MASCOT_RATIO = 320f / 426f    // 320×426 ≈ 0.751

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
            // ① 三张插图：**水平居中**（用户点名，改过两版）。
            //    ⚠️ 上一版用 `Row(依赖内容宽度) + align(CenterHorizontally)`，用户看真机说
            //    「不像居中」；现在用最不会出错的那种：外层 `Box(fillMaxWidth)` 撑满可用宽度，
            //    内层 `Row` 只包三张图，居中交给外层的 `contentAlignment` —— 不依赖任何
            //    「这个 Row 有多宽」的推断。
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
                    // `ContentScale.FillBounds`（不是 Fit）：宽高都已经按真实长宽比给死了，
                    // 再交给 Fit 去"自己判断"反而会被 drawable 报的固有尺寸带偏。
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

            // ② 版本号：用户要求紧贴三张插图下方。
            Text(
                text = stringResource(R.string.about_version, version.first, version.second),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ③ 开源代码卡：整块可点，卡面自带「点这里」的提示 + 末尾箭头。
            RepoCard(onClick = { openRepo(ctx) })

            Spacer(Modifier.height(12.dp))

            // ④ 致谢卡：**紧跟在「开源代码」卡下面**（用户 3.3 点名这个位置，别挪到页尾）。
            VibedCard()

            Spacer(Modifier.height(12.dp))

            // ⑤ **无标题**正文卡（用户 3.3：只去掉「它怎么做到的」这个标题，正文要留着）。
            StepCard {
                BodyText(stringResource(R.string.about_what_body))
            }

            Spacer(Modifier.height(12.dp))

            // ⑥ 两种用法。
            TitledCard(stringResource(R.string.about_how_title)) {
                BodyText(stringResource(R.string.about_how_body))
            }

            Spacer(Modifier.height(12.dp))

            // ⑦ 设置在哪。
            TitledCard(stringResource(R.string.about_where_title)) {
                BodyText(stringResource(R.string.about_where_body))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * 「Vibed by」卡：浅色底（同正文卡的 `surfaceContainerLow`）+ 图标在左边 + 一行字。
 * 用户 3.3 指定：**位置在「开源代码」卡正下方**（不是页尾），图标用他给的 `icon-idea.svg`
 * （由 `tools/export_about_images.py` 原样搬运成 `ic_idea.xml`）。
 */
@Composable
private fun VibedCard() {
    StepCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_idea),
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
 * 解码插图 → 优先拿"会动的那个"。三个坑（assets 目录不存在、`BitmapFactory` 只给首帧、
 * `ImageDecoder.createSource` 不收 InputStream）与逐级兜底都写在 `MascotAnimation.kt` 里。
 */
@Composable
private fun rememberMascot(): Drawable? {
    val ctx = LocalContext.current
    return remember { loadMascot(ctx) }
}

/** 一张插图：高度固定 [ILLUSTRATION_HEIGHT]，宽度由调用方按长宽比算好传进来。 */
@Composable
private fun Illustration(resId: Int, width: Dp) {
    Image(
        painter = painterResource(resId),
        // 插图是纯装饰，语义由正文承担。
        contentDescription = null,
        modifier = Modifier
            .height(ILLUSTRATION_HEIGHT)
            .width(width),
        // 尺寸已经按真实长宽比给死，这里不需要再让 painter 去"适配"。
        contentScale = ContentScale.FillBounds
    )
}

/**
 * 会动的插图：**自己驱动帧**，不指望 drawable 自己会走。
 *
 * ⭐ 为什么必须自己驱动（用户 3.3 反馈「gif 不能动弹」的直接原因）：
 *  `AnimatedImageDrawable`（API 28+ 解动画 WebP/GIF 得到的那个）靠**宿主注册的回调**
 *  来决定何时推进到下一帧；`View` 体系里是 `setCallback(view)` 帮你做了这件事，
 *  而 Compose 里我们只是把 drawable 画到画布上，**没有任何人给它回调** ——
 *  于是它一直停在第一帧。第一版就是这么静止的。
 *
 *  做法：`withFrameNanos` 每帧 invalidate 一次（`DrawScope` 会重画 ⇒ drawable 的
 *  `draw()` 被反复调用、内部按时间推进），同时显式 `start()`（`Animatable` 接口，
 *  `AnimatedImageDrawable` 和 `AnimationDrawable` 都有）。循环条件是 `isRunning`，
 *  播完/停掉就自动退出，不会白烧 CPU。
 *
 *  这里**不用** `ContentScale`：宽高由调用方按真实长宽比给死，直接铺满即可，
 *  免得再被 drawable 报的固有尺寸带偏（那正是"被横向拉伸"的来源）。
 */
@Composable
private fun AnimatedImage(drawable: Drawable, modifier: Modifier = Modifier) {
    LaunchedEffect(drawable) {
        // ⚠️ 顺序：先 start() 再看 isRunning。上一版按 `running = isRunning` 的初值去循环，
        // 首次组合时它还是 false（还没 start），于是 while 一次都不进 —— 图还是不动。
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
 * 「开源代码」卡：GitHub 标 + `owner/repo` + 「点击访问 GitHub 项目」+ 末尾箭头。
 *
 * 用户 3.3 的三条要求都落在这里：标题是**开源代码**（不是「项目主页」）、
 * 去掉了原先那行小字说明、并且**要让人看得出能点** —— 所以卡面自带一句
 * 「点击访问 GitHub 项目」，右边再放一个 `chevron_right` 箭头（惯用的"这里能进去"提示），
 * 整卡 `clickable`（MD3 自带水波纹反馈）。
 *
 * 用 [MaterialTheme.colorScheme.primaryContainer] 底（浅红）：和正文卡
 * （surfaceContainerLow，近白）明显分开 —— 要的是「像名片」，不是普通正文。
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
 * 右箭头（`chevron_right` 的几何：两条 45° 线）。
 * 用 [PathBuilder] 画 —— 它的方法名是后缀式的（相对 = `moveToRelative`/`lineToRelative`），
 * 跟 `Path` 上那套名字不一样。
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
 * 链接交给别的应用（否则在 resolve 阶段就被拒）。本App **一行联网代码都没有**，
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
        // ActivityNotFoundException 是预期内的；其他 Throwable 也一并兜住：
        // 不要再因为这一下点击把整页崩掉。
        Log.w(TAG, "打开仓库链接失败（${t.javaClass.simpleName}）：${t.message}")
        Toast.makeText(ctx, ctx.getString(R.string.about_repo_failed), Toast.LENGTH_LONG).show()
    }
}
