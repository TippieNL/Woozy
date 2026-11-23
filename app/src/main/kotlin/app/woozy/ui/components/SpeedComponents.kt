package app.woozy.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.woozy.R
import app.woozy.domain.model.SpeedLimit
import app.woozy.domain.model.SpeedUnit
import app.woozy.ui.theme.WoozyColors
import app.woozy.ui.theme.WoozyType

/**
 * Round speed-limit sign: white centre, red ring, black numerals. Shows "--" when the limit is
 * unknown (never a guessed value). Pulses while [isSpeeding].
 */
@Composable
fun SpeedLimitSign(limit: SpeedLimit, isSpeeding: Boolean, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val scale = if (isSpeeding) {
        val transition = rememberInfiniteTransition(label = "speeding")
        val s by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
            label = "speedingScale",
        )
        s
    } else {
        1f
    }
    val known = limit as? SpeedLimit.Known
    val description = known?.let { stringResource(R.string.speed_limit_value, it.kmh) }
        ?: stringResource(R.string.speed_limit_unknown)
    val ringWidth = with(LocalDensity.current) { (size * 0.13f).toPx() }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val radius = this.size.minDimension / 2
            drawCircle(Color.White, radius)
            drawCircle(
                color = WoozyColors.SignRed,
                radius = radius - ringWidth / 2,
                center = Offset(this.size.width / 2, this.size.height / 2),
                style = Stroke(width = ringWidth),
            )
        }
        val text = known?.kmh?.toString() ?: "--"
        Text(
            text = text,
            style = TextStyle(
                color = if (known != null) Color.Black else Color(0xFF8A8F96),
                fontWeight = FontWeight.Bold,
                fontSize = when {
                    text.length >= 3 -> (size.value * 0.30f).sp
                    else -> (size.value * 0.38f).sp
                },
                letterSpacing = (-0.5).sp,
            ),
        )
    }
}

/** GPS speed readout. Null speed renders "--" (no fix / GPS lost). */
@Composable
fun CurrentSpeed(speed: Int?, unit: SpeedUnit, isSpeeding: Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.current_speed)
    Column(modifier = modifier.semantics { contentDescription = "$label ${speed ?: "--"}" }) {
        Text(
            text = speed?.toString() ?: "--",
            style = WoozyType.SpeedValue,
            color = if (isSpeeding) WoozyColors.Danger else WoozyColors.TextPrimary,
        )
        Text(
            text = stringResource(if (unit == SpeedUnit.KMH) R.string.settings_kmh else R.string.settings_mph),
            style = WoozyType.SpeedUnit,
        )
    }
}
