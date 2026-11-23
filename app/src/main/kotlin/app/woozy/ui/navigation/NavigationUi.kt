package app.woozy.ui.navigation

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.woozy.R
import app.woozy.domain.model.ManeuverKind
import app.woozy.domain.model.Route
import app.woozy.domain.model.RouteOptions
import app.woozy.domain.model.RouteType
import app.woozy.navigation.NavigationState
import app.woozy.navigation.RouteError
import app.woozy.navigation.RouteProgress
import app.woozy.ui.theme.WoozyColors
import app.woozy.util.formatDistance
import java.util.Date
import kotlin.math.roundToInt

// region formatting

@Composable
fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) stringResource(R.string.duration_min, minutes)
    else stringResource(R.string.duration_hour_min, minutes / 60, minutes % 60)
}

@Composable
fun formatEta(secondsFromNow: Double): String {
    val context = LocalContext.current
    return DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (secondsFromNow * 1000).toLong()))
}

/** Maneuver distances: 10 m steps below 100 m, 50 m steps below 1 km. */
fun formatManeuverDistance(meters: Double, imperial: Boolean): String = when {
    imperial -> formatDistance(meters, true)
    meters < 100 -> "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} m"
    meters < 1_000 -> "${(meters / 50).roundToInt() * 50} m"
    else -> formatDistance(meters, false)
}

// endregion

/** Arrow for a maneuver; left-hand variants mirror the right-hand drawable. */
@Composable
fun ManeuverIcon(kind: ManeuverKind, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Color.White) {
    val (icon: Int, mirrored: Boolean) = when (kind) {
        ManeuverKind.RIGHT -> R.drawable.ic_turn_right to false
        ManeuverKind.LEFT -> R.drawable.ic_turn_right to true
        ManeuverKind.SLIGHT_RIGHT, ManeuverKind.RAMP_RIGHT -> R.drawable.ic_turn_slight_right to false
        ManeuverKind.SLIGHT_LEFT, ManeuverKind.RAMP_LEFT -> R.drawable.ic_turn_slight_right to true
        ManeuverKind.SHARP_RIGHT -> R.drawable.ic_turn_sharp_right to false
        ManeuverKind.SHARP_LEFT -> R.drawable.ic_turn_sharp_right to true
        ManeuverKind.KEEP_RIGHT -> R.drawable.ic_keep_right to false
        ManeuverKind.KEEP_LEFT -> R.drawable.ic_keep_right to true
        ManeuverKind.UTURN -> R.drawable.ic_turn_uturn to false
        ManeuverKind.ROUNDABOUT_ENTER, ManeuverKind.ROUNDABOUT_EXIT -> R.drawable.ic_roundabout to false
        ManeuverKind.MERGE -> R.drawable.ic_merge to false
        ManeuverKind.ARRIVE -> R.drawable.ic_pin to false
        ManeuverKind.DEPART -> R.drawable.ic_navigation to false
        ManeuverKind.STRAIGHT, ManeuverKind.FERRY -> R.drawable.ic_turn_straight to false
    }
    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size).scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f),
    )
}

/** Top banner during guidance: arrow, distance, instruction, and a "then" hint for close follow-ups. */
@Composable
fun ManeuverBanner(nav: NavigationState.Navigating, imperial: Boolean, modifier: Modifier = Modifier) {
    val progress = nav.progress
    val index = progress?.nextManeuverIndex
    val maneuver = index?.let { nav.route.maneuvers.getOrNull(it) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(WoozyColors.Accent),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nav.rerouting) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.nav_recalculating), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                return@Row
            }
            ManeuverIcon(maneuver?.kind ?: ManeuverKind.STRAIGHT, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = progress?.let { formatManeuverDistance(it.distanceToNextManeuver, imperial) } ?: "…",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = maneuver?.instruction ?: nav.route.maneuvers.firstOrNull()?.instruction.orEmpty(),
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // "Then …" strip when the following maneuver comes quickly after this one.
        val following = index?.let { nav.route.maneuvers.getOrNull(it + 1) }
        if (!nav.rerouting && maneuver != null && following != null && maneuver.lengthMeters < 250 && following.kind != ManeuverKind.ARRIVE) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.nav_then), fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.width(8.dp))
                ManeuverIcon(following.kind, size = 22.dp)
            }
        }
    }
}

/** Bottom sheet content while choosing a route. */
@Composable
fun RoutePreviewPanel(
    preview: NavigationState.Previewing,
    options: RouteOptions,
    imperial: Boolean,
    onSelect: (Int) -> Unit,
    onOptionsChange: (RouteOptions) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(WoozyColors.PanelHigh)
            .padding(16.dp),
    ) {
        Text(
            preview.destination.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(stringResource(R.string.route_fastest), options.type == RouteType.FASTEST) { onOptionsChange(options.copy(type = RouteType.FASTEST)) }
            OptionChip(stringResource(R.string.route_shortest), options.type == RouteType.SHORTEST) { onOptionsChange(options.copy(type = RouteType.SHORTEST)) }
            OptionChip(stringResource(R.string.route_avoid_tolls), options.avoidTolls) { onOptionsChange(options.copy(avoidTolls = !options.avoidTolls)) }
            OptionChip(stringResource(R.string.route_avoid_highways), options.avoidHighways) { onOptionsChange(options.copy(avoidHighways = !options.avoidHighways)) }
            OptionChip(stringResource(R.string.route_avoid_ferries), options.avoidFerries) { onOptionsChange(options.copy(avoidFerries = !options.avoidFerries)) }
        }
        Spacer(Modifier.height(12.dp))
        when {
            preview.loading -> Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = WoozyColors.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.route_loading), style = MaterialTheme.typography.bodyLarge)
            }
            preview.error != null -> Text(
                stringResource(
                    when (preview.error) {
                        RouteError.NO_LOCATION -> R.string.route_error_location
                        RouteError.NETWORK -> R.string.route_error_network
                        RouteError.NO_ROUTE -> R.string.route_error_no_route
                        RouteError.SERVER -> R.string.route_error_server
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = WoozyColors.Warning,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.routes.forEachIndexed { i, route ->
                    RouteOptionRow(route, selected = i == preview.selected, fastest = i == fastestIndex(preview.routes), imperial = imperial) { onSelect(i) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.height(60.dp).weight(1f), shape = RoundedCornerShape(30.dp)) {
                Text(stringResource(R.string.cancel), fontSize = 18.sp, color = WoozyColors.TextPrimary)
            }
            Button(
                onClick = onStart,
                enabled = preview.selectedRoute != null,
                modifier = Modifier.height(60.dp).weight(1.6f),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoozyColors.Accent),
            ) { Text(stringResource(R.string.nav_start), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun fastestIndex(routes: List<Route>): Int = routes.indices.minByOrNull { routes[it].durationSeconds } ?: -1

@Composable
private fun RouteOptionRow(route: Route, selected: Boolean, fastest: Boolean, imperial: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) WoozyColors.Accent.copy(alpha = 0.16f) else WoozyColors.PanelLow)
            .border(2.dp, if (selected) WoozyColors.Accent else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(route.durationSeconds), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WoozyColors.TextPrimary)
                if (fastest) {
                    Spacer(Modifier.width(8.dp))
                    Badge(stringResource(R.string.route_badge_fastest), WoozyColors.Success)
                }
            }
            val flags = buildList {
                if (route.hasToll) add(stringResource(R.string.route_badge_toll))
                if (route.hasFerry) add(stringResource(R.string.route_badge_ferry))
            }
            Text(
                (listOf(formatDistance(route.lengthMeters, imperial), stringResource(R.string.nav_arrival, formatEta(route.durationSeconds))) + flags)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) Color.White else WoozyColors.TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) WoozyColors.Accent else WoozyColors.PanelLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Trip summary row shown under the speed/report row while navigating. */
@Composable
fun TripSummaryRow(progress: RouteProgress?, route: Route, imperial: Boolean, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val remainingSeconds = progress?.remainingSeconds ?: route.durationSeconds
    val remainingMeters = progress?.remainingDistance ?: route.lengthMeters
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WoozyColors.Field)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatEta(remainingSeconds), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WoozyColors.TextPrimary)
            Text(
                "${formatDuration(remainingSeconds)} · ${formatDistance(remainingMeters, imperial)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(WoozyColors.Danger)
                .clickable(onClickLabel = stringResource(R.string.nav_end), onClick = onEnd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.nav_end), tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
