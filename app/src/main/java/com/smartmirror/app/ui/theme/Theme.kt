package com.smartmirror.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── 品牌色板 · 灵感：高定时装屋的试衣沙龙 ─────────────────────────────────
// 暗紫绒布墙面 × 暖金五金件 × 奶油色大理石台面

private val VelvetPlum       = Color(0xFF4A1A6B)   // 主色：深李子紫（天鹅绒质感）
private val MauveDust        = Color(0xFF8E6B9E)   // 辅色：灰紫
private val LilacMist        = Color(0xFFF3E5F5)   // 主色容器：淡紫雾
private val WarmGold          = Color(0xFFC8A45C)   // 强调色：暖金（只在评分星星 & 主CTA出现）
private val GoldPale          = Color(0xFFFFF0D4)   // 金色容器
private val WarmCream         = Color(0xFFFBF7F2)   // 背景：暖奶油（大理石的温润感）
private val CardWhite         = Color(0xFFFFFBF6)   // 卡片：暖白
private val CharcoalInk       = Color(0xFF1E1B1A)   // 主文字：炭黑
private val SoftBlush         = Color(0xFFF0E6E0)   // 表面变体：柔粉灰
private val TaupeGray         = Color(0xFF6B605C)   // 辅助文字：灰褐
private val DeepEspresso      = Color(0xFF141210)   // 暗色背景：深烘浓缩

// ── 亮色主题 ──────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = VelvetPlum,
    onPrimary = Color.White,
    primaryContainer = LilacMist,
    onPrimaryContainer = Color(0xFF3A0E52),
    secondary = WarmGold,
    onSecondary = Color.White,
    secondaryContainer = GoldPale,
    onSecondaryContainer = Color(0xFF3E2C00),
    tertiary = Color(0xFF7D5E50),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC7),
    onTertiaryContainer = Color(0xFF3B2016),
    background = WarmCream,
    onBackground = CharcoalInk,
    surface = CardWhite,
    onSurface = CharcoalInk,
    surfaceVariant = SoftBlush,
    onSurfaceVariant = TaupeGray,
    surfaceTint = VelvetPlum,
    inverseSurface = Color(0xFF38322E),
    inverseOnSurface = Color(0xFFF5EFE8),
    outline = Color(0xFFD6CFC7),
    outlineVariant = Color(0xFFEBE4DC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ── 暗色主题 · 幽暗试衣间的氛围 ──────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4A8F0),
    onPrimary = Color(0xFF3A0E52),
    primaryContainer = Color(0xFF4E1D72),
    onPrimaryContainer = LilacMist,
    secondary = Color(0xFFE8C96A),
    onSecondary = Color(0xFF3E2C00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = GoldPale,
    tertiary = Color(0xFFE8BEA8),
    onTertiary = Color(0xFF502C1D),
    background = DeepEspresso,
    onBackground = Color(0xFFECE0D5),
    surface = Color(0xFF1F1C1A),
    onSurface = Color(0xFFECE0D5),
    surfaceVariant = Color(0xFF3D3732),
    onSurfaceVariant = Color(0xFFCBC1B8),
    surfaceTint = Color(0xFFD4A8F0),
    inverseSurface = Color(0xFFECE0D5),
    inverseOnSurface = Color(0xFF38322E),
    outline = Color(0xFF938C84),
    outlineVariant = Color(0xFF3D3732),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ── 排版 · 衬线标题 × 无衬线正文 ─────────────────────────────────────────
// 衬线体自带"编辑感"，就像 VOGUE 内页的标题；
// 无衬线正文保证在试衣镜这种远距离屏幕上的可读性。

private val DisplayFont = FontFamily.Serif   // Android 系统衬线体 (Noto Serif)
private val BodyFont    = FontFamily.Default  // Roboto / Noto Sans

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5f).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25f).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15f.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1f.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5f.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25f.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4f.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1f.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5f.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5f.sp,
    ),
)

// ── 形状 · 柔和圆角 — 试衣沙龙没有尖角 ─────────────────────────────────

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small       = RoundedCornerShape(10.dp),
    medium      = RoundedCornerShape(16.dp),
    large       = RoundedCornerShape(20.dp),
    extraLarge  = RoundedCornerShape(28.dp),
)

// ── 主题入口 ─────────────────────────────────────────────────────────────

@Composable
fun SmartMirrorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            try {
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } catch (_: Exception) {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val activity = view.context as? Activity ?: return@SideEffect
                val window = activity.window
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            } catch (_: Exception) { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
