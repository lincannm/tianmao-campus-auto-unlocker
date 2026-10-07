package com.fucktmall.unlockassistant.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fucktmall.unlockassistant.R
import com.fucktmall.unlockassistant.Session
import com.fucktmall.unlockassistant.widget.UnlockWidget1x1
import com.fucktmall.unlockassistant.widget.UnlockWidget2x1
import com.fucktmall.unlockassistant.widget.UnlockWidget2x2
import com.fucktmall.unlockassistant.widget.UnlockWidget4x2
import com.fucktmall.unlockassistant.widget.WidgetPinnedReceiver

/**
 * 「小组件添加至桌面」引导页（独立整页，入口是设置页那个空心按钮）。
 *
 * 两件事按顺序做：**先授「桌面快捷方式」权限，再调系统的「固定小部件」请求**。
 * ⚠️ 顺序不能反 —— 没给权限时 `requestPinAppWidget` 在厂商桌面上可能回 true 却什么都没发生，
 * 用户看到的是「点了没反应」，所以这一页必须先把权限说完。
 *
 * 自动添加**不是所有桌面都支持**（`isRequestPinAppWidgetSupported`），
 * 所以页尾永远给一段手动添加的步骤，不把用户堵在一条路上。
 */

/** 一档尺寸：显示名、说明、以及它对应的那个小部件 receiver。 */
private class PinOption(val nameRes: Int, val descRes: Int, val provider: Class<*>)

/** 四张卡的文案直接复用小部件自己的 `widget_name_*` / `widget_desc_*`，不另写一套。 */
private val PIN_OPTIONS = listOf(
    PinOption(R.string.widget_name_1x1, R.string.widget_desc_1x1, UnlockWidget1x1::class.java),
    PinOption(R.string.widget_name_2x1, R.string.widget_desc_2x1, UnlockWidget2x1::class.java),
    PinOption(R.string.widget_name_2x2, R.string.widget_desc_2x2, UnlockWidget2x2::class.java),
    PinOption(R.string.widget_name_4x2, R.string.widget_desc_4x2, UnlockWidget4x2::class.java)
)

@Composable
fun WidgetPinScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableIntStateOf(0) }
    val supported = remember { pinSupported(ctx) }

    Screen {
        ScreenHeader(title = stringResource(R.string.wpin_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            StepsCard(
                title = stringResource(R.string.wpin_perm_steps_title),
                steps = listOf(
                    stringResource(R.string.wpin_perm_step_1),
                    stringResource(R.string.wpin_perm_step_2),
                    stringResource(R.string.wpin_perm_step_3)
                )
            )

            Spacer(Modifier.height(10.dp))
            OutlinedActionButton(
                text = stringResource(R.string.wpin_perm_btn),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!openShortcutPermission(ctx)) {
                        Toast.makeText(ctx, R.string.wpin_perm_open_failed, Toast.LENGTH_LONG).show()
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            HintText(stringResource(R.string.wpin_perm_hint))

            Spacer(Modifier.height(12.dp))

            StepCard {
                SectionTitle(stringResource(R.string.wpin_pick_title))
                Spacer(Modifier.height(4.dp))
                PIN_OPTIONS.forEachIndexed { i, option ->
                    SizeRow(option = option, selected = i == selected, onSelect = { selected = i })
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(text = stringResource(R.string.wpin_add_btn)) {
                    val option = PIN_OPTIONS[selected]
                    val name = ctx.getString(option.nameRes)
                    // 返回值只说明桌面**受理**了这次请求；到底加没加上由 WidgetPinnedReceiver
                    // 那个回调来说。所以两种结果都明说，不静默。
                    if (requestPin(ctx, option.provider)) {
                        Session.addLog("已请求把「$name」加到桌面")
                        Toast.makeText(ctx, R.string.wpin_sent, Toast.LENGTH_LONG).show()
                    } else {
                        Session.addLog("桌面没接受添加小部件请求（$name）")
                        Toast.makeText(ctx, R.string.wpin_refused, Toast.LENGTH_LONG).show()
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintText(
                    stringResource(
                        if (supported) R.string.wpin_add_hint_ok else R.string.wpin_add_hint_unsupported
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            StepsCard(
                title = stringResource(R.string.wpin_manual_title),
                steps = listOf(
                    stringResource(R.string.wpin_manual_step_1),
                    stringResource(R.string.wpin_manual_step_2),
                    stringResource(R.string.wpin_manual_step_3)
                )
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 可选中的一行：左边单选圆点，右边尺寸名 + 一句说明。整行可点，不用非点到圆点上。 */
@Composable
private fun SizeRow(option: PinOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = stringResource(option.nameRes),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(option.descRes),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 卡内小标题（与「了解本App」页的卡标题同一档）。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * 桌面支不支持「自动添加小部件」。
 *
 * `isRequestPinAppWidgetSupported` 是 **API 26+** 才有的（minSdk 24），低版本直接算不支持。
 * 厂商桌面有实现但**谎报 false** 的可能，所以这个值只用来换提示文案，
 * **不用来禁用按钮** —— 禁用了用户只会看到点不动的按钮，而我们连失败原因都报不出来。
 */
private fun pinSupported(ctx: Context): Boolean = try {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported
} catch (t: Throwable) {
    false
}

/** 走系统「固定小部件」请求；返回桌面有没有受理（受理 ≠ 真的加上去了，见 [pinnedCallback]）。 */
private fun requestPin(ctx: Context, provider: Class<*>): Boolean {
    if (!pinSupported(ctx)) return false
    return try {
        // extras 给空 Bundle 而不是 null：部分桌面拿到 null 会直接崩（这不是我们控制得了的代码）。
        // 成功回调见 pinnedCallback：只有它才能证明小部件真的落在桌面上。
        AppWidgetManager.getInstance(ctx).requestPinAppWidget(
            ComponentName(ctx, provider),
            Bundle(),
            pinnedCallback(ctx)
        )
    } catch (t: Throwable) {
        false
    }
}

/**
 * 「小部件真的加到桌面上了」的回调广播（发给 [com.fucktmall.unlockassistant.widget.WidgetPinnedReceiver]）。
 *
 * ⚠️ 这个 PendingIntent 必须 **`FLAG_MUTABLE`**：它是**给桌面（系统）去发**的，
 * 桌面要往里塞分配到的 `EXTRA_APPWIDGET_ID`；`FLAG_IMMUTABLE` 会让系统填不进 extras。
 * Android 12+ 起可变性必须显式声明，所以两个 flag 都要给。
 */
private fun pinnedCallback(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
    ctx,
    0,
    Intent(ctx, WidgetPinnedReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
)

/**
 * 打开「桌面快捷方式」权限页。
 *
 * 权限名与页面都在厂商的 SecurityCenter 里，不在 AOSP 里：MIUI / HyperOS 上试两个已知的
 * 权限编辑页，都没有（别的品牌，或页面改名了）就退到系统「应用信息」页 ——
 * 那里至少能进「权限管理」，用户照引导页的步骤还能走到。
 *
 * 返回 false 表示连应用信息页都打不开（极其少见），由调用方 toast 说明。
 */
private fun openShortcutPermission(ctx: Context): Boolean {
    val candidates = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity"
        ),
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
        )
    )
    for (component in candidates) {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setComponent(component)
            .putExtra("extra_pkgname", ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 先解析再启动：厂商组件不存在时 startActivity 会抛，而且有的页面是签名保护的，
        // 解析能过滤掉大部分明显不可能的，剩下的交给下面的 catch。
        if (ctx.packageManager.resolveActivity(intent, 0) == null) continue
        try {
            ctx.startActivity(intent)
            return true
        } catch (t: Throwable) {
            // 试下一个候选
        }
    }
    return try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        false
    }
}
