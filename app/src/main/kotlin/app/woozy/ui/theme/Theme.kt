package app.woozy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object WoozyColors {
    val Black = Color(0xFF000000)
    val MapBackground = Color(0xFF0B0C0E)
    val PanelHigh = Color(0xFF282B30)
    val PanelLow = Color(0xFF1C1E21)
    val Field = Color(0xFF2E3237)
    val Pill = Color(0xFF2C2F34)
    val Divider = Color(0xFF33373D)
    val Accent = Color(0xFF138BFF)
    val AccentPressed = Color(0xFF0A6FD6)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9EA4AB)
    val TextTertiary = Color(0xFF6C727A)
    val SignRed = Color(0xFFE0312B)
    val Danger = Color(0xFFFF453A)
    val Warning = Color(0xFFFFB020)
    val Success = Color(0xFF32D74B)
}

private val scheme = darkColorScheme(
    primary = WoozyColors.Accent,
    onPrimary = Color.White,
    secondary = WoozyColors.Accent,
    background = WoozyColors.Black,
    onBackground = WoozyColors.TextPrimary,
    surface = WoozyColors.PanelLow,
    onSurface = WoozyColors.TextPrimary,
    surfaceVariant = WoozyColors.PanelHigh,
    onSurfaceVariant = WoozyColors.TextSecondary,
    surfaceContainer = WoozyColors.PanelLow,
    surfaceContainerHigh = WoozyColors.PanelHigh,
    surfaceContainerLow = WoozyColors.PanelLow,
    outline = WoozyColors.Divider,
    outlineVariant = WoozyColors.Divider,
    error = WoozyColors.Danger,
)

private val typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, color = WoozyColors.TextSecondary),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

object WoozyType {
    val SpeedValue = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
    val SpeedUnit = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WoozyColors.TextSecondary)
    val ReportButton = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun WoozyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
