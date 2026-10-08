package com.fucktmall.unlockassistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题：**红色**，`primary = #E53935`（Material Red 600），中性色是同一套带红味的暖灰。
 *
 * 没有用 dynamicLightColorScheme（API 31+ 从壁纸取色）：界面颜色随机器变会让排查问题变麻烦。
 *
 * ⚠️ 本主题里 `primaryContainer` 与 `errorContainer` 的值**是同一个** `#FFDAD6`
 * （同一套 MD3 红色调色板的 tone90），所以「已达成 / 未达成」两只状态胶囊必须一只用
 * `primaryContainer`、另一只改用实心 `error` —— 详见 [StatusChip]。
 * `tertiary`（暖琥珀金）只给 [TroubleshootCard] 用：向导里那张排查卡要跟正常步骤卡一眼分开。
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
