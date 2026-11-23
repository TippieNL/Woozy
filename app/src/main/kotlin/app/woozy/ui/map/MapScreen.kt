package app.woozy.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.woozy.R
import app.woozy.domain.model.SpeedUnit
import app.woozy.ui.components.CurrentSpeed
import app.woozy.ui.components.RoundMapButton
import app.woozy.ui.components.SpeedLimitSign
import app.woozy.ui.components.StatusChip
import app.woozy.ui.components.rememberSpeechInput
import app.woozy.ui.permissions.LocationAccessCard
import app.woozy.ui.reports.ReportSheet
import app.woozy.ui.theme.WoozyColors
import app.woozy.ui.theme.WoozyType
import app.woozy.util.formatDistance

/** Main driving screen: full-bleed map with the speed / report / search panel at the bottom. */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenSearch: (initialQuery: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controller = remember { MapController(context) }
    val isFollowing by controller.isFollowing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showReportSheet by remember { mutableStateOf(false) }
    val currentOnOpenSearch by rememberUpdatedState(onOpenSearch)

    // GPS → map, outside composition. Stops with the lifecycle so GPS turns off in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(controller, lifecycleOwner) {
        controller.onRoadNameChanged = viewModel::onRoadNameChanged
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.fixes.collect(controller::updateLocation)
        }
    }
    LaunchedEffect(state.settings) { controller.applySettings(state.settings) }
    LaunchedEffect(state.reports) { controller.setReports(state.reports) }
    LaunchedEffect(state.destination) { controller.setDestination(state.destination) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is MapEvent.Message -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(event.text)
                }
                is MapEvent.OpenSearch -> currentOnOpenSearch(event.query)
            }
        }
    }
    KeepScreenOn(state.settings.keepScreenOn)

    val startVoice = rememberSpeechInput(
        prompt = stringResource(R.string.voice_prompt),
        onResult = viewModel::onVoiceTranscript,
        onUnavailable = viewModel::onVoiceUnavailable,
    )

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    var screenHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(screenHeight, panelHeight, statusBarTop) {
        controller.setViewport(MapController.Viewport(screenHeight, statusBarTop, panelHeight))
    }

    Box(
        modifier
            .fillMaxSize()
            .background(WoozyColors.MapBackground)
            .onSizeChanged { screenHeight = it.height },
    ) {
        WoozyMap(controller, Modifier.fillMaxSize())

        // ── Top: road name + GPS/connectivity status + permission cards + messages ──
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.roadName?.let { RoadNamePill(it) }
            StatusChips(state)
            LocationAccessCard(
                permission = state.permission,
                locationEnabled = state.locationEnabled,
                onPermissionResult = viewModel::onPermissionResult,
            )
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = WoozyColors.PanelHigh,
                    contentColor = WoozyColors.TextPrimary,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }

        // ── Bottom: floating buttons, destination card, main panel ──
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(
                Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                AnimatedVisibility(visible = !isFollowing && state.gpsStatus != GpsStatus.NO_PERMISSION, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    RoundMapButton(R.drawable.ic_navigation, stringResource(R.string.recenter), onClick = controller::recenter)
                }
                RoundMapButton(
                    icon = if (state.settings.voiceAlertsEnabled) R.drawable.ic_volume_on else R.drawable.ic_volume_off,
                    contentDescription = stringResource(
                        if (state.settings.voiceAlertsEnabled) R.string.voice_alerts_on else R.string.voice_alerts_off,
                    ),
                    tint = if (state.settings.voiceAlertsEnabled) WoozyColors.Accent else WoozyColors.TextSecondary,
                    onClick = viewModel::toggleVoiceAlerts,
                )
            }
            Text(
                stringResource(R.string.map_attribution),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 6.dp),
            )
            state.destination?.let { place ->
                DestinationCard(
                    name = place.name,
                    address = place.address,
                    distance = state.destinationDistanceMeters?.let {
                        stringResource(R.string.distance_straight_line, formatDistance(it, state.settings.speedUnit == SpeedUnit.MPH))
                    },
                    onClose = viewModel::clearDestination,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            BottomPanel(
                state = state,
                onReport = { showReportSheet = true },
                onSearch = { onOpenSearch("") },
                onVoice = startVoice,
                modifier = Modifier.onSizeChanged { panelHeight = it.height },
            )
        }
    }

    if (showReportSheet) {
        ReportSheet(
            onReport = { type ->
                showReportSheet = false
                viewModel.submitReport(type)
            },
            onDismiss = { showReportSheet = false },
        )
    }
}

@Composable
private fun BottomPanel(
    state: MapUiState,
    onReport: () -> Unit,
    onSearch: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            // Black behind the lower half so the panel's bottom corners sit on the nav bar colour.
            .drawBehind { drawRect(Color.Black, Offset(0f, size.height / 2), Size(size.width, size.height / 2)) }
            .clip(RoundedCornerShape(28.dp))
            .background(WoozyColors.PanelLow),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(WoozyColors.PanelHigh)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4A4E55)),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedLimitSign(state.speedLimit, state.isSpeeding, size = 66.dp)
                Spacer(Modifier.width(14.dp))
                CurrentSpeed(state.speed, state.settings.speedUnit, state.isSpeeding)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onReport,
                    modifier = Modifier
                        .height(68.dp)
                        .width(190.dp),
                    shape = RoundedCornerShape(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WoozyColors.Accent, contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.report), style = WoozyType.ReportButton)
                }
            }
        }
        SearchField(onSearch = onSearch, onVoice = onVoice, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun SearchField(onSearch: () -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WoozyColors.Field)
            .clickable(onClick = onSearch)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = WoozyColors.TextSecondary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.where_to),
            fontSize = 20.sp,
            color = WoozyColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onVoice, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.voice_command), tint = WoozyColors.TextSecondary)
        }
    }
}

@Composable
private fun RoadNamePill(name: String) {
    Text(
        text = name,
        fontSize = 17.sp,
        color = WoozyColors.TextPrimary,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(WoozyColors.PanelHigh.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
private fun StatusChips(state: MapUiState) {
    val gps = when (state.gpsStatus) {
        GpsStatus.SEARCHING -> stringResource(R.string.gps_searching) to WoozyColors.Warning
        GpsStatus.WEAK -> stringResource(R.string.gps_weak) to WoozyColors.Warning
        GpsStatus.LOST -> stringResource(R.string.gps_lost) to WoozyColors.Danger
        // OK needs no chip; permission / location-off states get a full explanation card instead.
        GpsStatus.OK, GpsStatus.NO_PERMISSION, GpsStatus.LOCATION_OFF -> null
    }
    gps?.let { (text, color) -> StatusChip(text, color) }
    if (!state.online) StatusChip(stringResource(R.string.offline), WoozyColors.TextTertiary)
}

@Composable
private fun DestinationCard(name: String, address: String?, distance: String?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WoozyColors.PanelHigh)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(WoozyColors.Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_pin), null, tint = WoozyColors.Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val secondary = listOfNotNull(distance, address).joinToString(" · ")
            if (secondary.isNotEmpty()) {
                Text(secondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = WoozyColors.TextSecondary)
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
