package com.fucktmall.unlockassistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题：**红色**（用户 3.0 要求，之前是 MD3 baseline 的紫色）。
 *
 * 色值取自 Material 的红色系：`primary = #E53935`（Material Red 600）。
 * **图标/配色修订起从 MD3 红 tone 40 `#B3261E` 换成它**（版本号没动，仍是 3.0 / versionCode 5）：
 * 用户反馈 tone 40 那支「发灰、显老气」，
 * 要求饱和度高一些（鲜艳）；同一轮里把桌面图标背景也换成同一支红，保证图标与界面同色。
 * 代价是白字对比度从 6.5:1 降到 4.2:1（仍 ≥4.5 一档的边界、远高于大字 3:1 的门槛），
 * 按钮是 20sp 加粗、进度条与开关都是大色块，实测观感没问题。
 * 中性色（卡片、对话框、日志底）仍然是同一套带红味的暖灰，`error` / `tertiary` 一个没动 ——
 * 「状态胶囊不能跟主题撞色」那条约束（见下）继续成立。
 *
 * 没有用 dynamicLightColorScheme（API 31+ 从壁纸取色）：界面颜色随机器变会让排查问题变麻烦。
 *
 * ## ⭐ 「已达成」的状态胶囊用什么颜色（改过三轮，这是第三轮）
 *
 * 难点：本主题里 `primaryContainer` 与 `errorContainer` 的值**是同一个** `#FFDAD6`
 * （同一套 MD3 红色调色板的 tone90），所以「已达成 / 未达成」不能一只用
 * primaryContainer、另一只还用 errorContainer —— 那样两只胶囊颜色完全一样。
 *
 * 走过的两版：
 *  - 第一版给「已达成」配了**绿**，用户反馈「跟红色主题不搭、显得突兀」；
 *  - 第二版改用 **tertiary（暖琥珀金）** `tertiaryContainer`；
 *  - **现在（用户最新反馈「不太符合主题色」）**：整页收敛回**红色一支色系**，靠深浅分状态：
 *      已达成 = `primaryContainer` `#FFDAD6` 浅红底 + `onPrimaryContainer` `#410002` 深红字（软）
 *      未达成 = `error` `#BA1A1A` 实心深红底 + `onError` 白字（硬）
 *    「坏消息」比「好消息」响亮，符合状态语义；两只的对比度分别是 13:1 与 6.5:1，都够。
 *  `tertiary`（暖琥珀金）本身**没有删**，色板里还在，只是当前没有界面元素在用它。
 *  取色一律走色板，不写死常量，改主题时会跟着变。
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF6E5C2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF9E0A7),
    onTertiaryContainer = Color(0xFF241A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF221A19),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF221A19),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EE),
    surfaceContainer = Color(0xFFFCEAE7),
    surfaceContainerHigh = Color(0xFFF6E4E1),
    surfaceContainerHighest = Color(0xFFF1DFDC),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BF),
    inverseSurface = Color(0xFF382E2D),
    inverseOnSurface = Color(0xFFFEEBE9),
    inversePrimary = Color(0xFFFFB4AB),
    scrim = Color(0xFF000000)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A80),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB8),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFDCC48E),
    onTertiary = Color(0xFF3C2F04),
    tertiaryContainer = Color(0xFF554619),
    onTertiaryContainer = Color(0xFFF9E0A7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DFDC),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DFDC),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceContainerLowest = Color(0xFF140C0B),
    surfaceContainerLow = Color(0xFF221A19),
    surfaceContainer = Color(0xFF261E1D),
    surfaceContainerHigh = Color(0xFF312827),
    surfaceContainerHighest = Color(0xFF3D3332),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341),
    inverseSurface = Color(0xFFF1DFDC),
    inverseOnSurface = Color(0xFF382E2D),
    inversePrimary = Color(0xFFE53935),
    scrim = Color(0xFF000000)
)

@Composable
fun UnlockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
