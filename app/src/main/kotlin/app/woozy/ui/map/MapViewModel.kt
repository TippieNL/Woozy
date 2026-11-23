package app.woozy.ui.map

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.woozy.R
import app.woozy.data.providers.SpeedLimitProvider
import app.woozy.data.repository.DestinationRepository
import app.woozy.data.repository.ReportRepository
import app.woozy.data.repository.SettingsRepository
import app.woozy.data.repository.SubmitResult
import app.woozy.domain.model.FavoriteKind
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.Place
import app.woozy.domain.model.ReportType
import app.woozy.domain.model.RouteOptions
import app.woozy.domain.model.SpeedLimit
import app.woozy.domain.model.SpeedUnit
import app.woozy.domain.model.TrafficReport
import app.woozy.domain.model.UserSettings
import app.woozy.location.ConnectivityMonitor
import app.woozy.location.LocationPermission
import app.woozy.location.LocationRepository
import app.woozy.location.LocationStatusMonitor
import app.woozy.location.SpeedCalculator
import app.woozy.navigation.NavigationEvent
import app.woozy.navigation.NavigationManager
import app.woozy.navigation.NavigationState
import app.woozy.services.VoiceAlertManager
import app.woozy.traffic.SpeedingAlerter
import app.woozy.ui.reports.style
import app.woozy.util.Geo
import app.woozy.voice.VoiceCommand
import app.woozy.voice.VoiceCommandParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

enum class GpsStatus { NO_PERMISSION, LOCATION_OFF, SEARCHING, OK, WEAK, LOST }

data class MapUiState(
    val permission: LocationPermission = LocationPermission.NOT_GRANTED,
    val locationEnabled: Boolean = true,
    val online: Boolean = true,
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
    /** Speed in the user's unit, null when unknown ("--"). */
    val speed: Int? = null,
    val speedLimit: SpeedLimit = SpeedLimit.Unknown,
    /** Posted limit converted to the user's unit (mph rounded to 5), null when unknown. */
    val speedLimitDisplay: Int? = null,
    val isSpeeding: Boolean = false,
    val roadName: String? = null,
    val settings: UserSettings = UserSettings(),
    val reports: List<TrafficReport> = emptyList(),
    val destination: Place? = null,
    val destinationDistanceMeters: Double? = null,
    val navigation: NavigationState = NavigationState.Idle,
)

sealed interface MapEvent {
    data class Message(val text: String) : MapEvent
    data class OpenSearch(val query: String) : MapEvent
}

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    locationRepository: LocationRepository,
    private val statusMonitor: LocationStatusMonitor,
    connectivityMonitor: ConnectivityMonitor,
    private val settingsRepository: SettingsRepository,
    private val reportRepository: ReportRepository,
    private val destinationRepository: DestinationRepository,
    speedLimitProvider: SpeedLimitProvider,
    private val navigation: NavigationManager,
    private val voice: VoiceAlertManager,
) : ViewModel() {

    /** Raw fixes for the map controller (collected outside composition → no recomposition per fix). */
    val fixes: SharedFlow<LocationFix> = locationRepository.fixes

    private val speedCalculator = SpeedCalculator()
    private val speedingAlerter = SpeedingAlerter()
    private val roadName = MutableStateFlow<String?>(null)

    /** Latest fix, for attaching reports. Updated only while the UI is subscribed. */
    private val latestFix = MutableStateFlow<LocationFix?>(null)

    private val events = Channel<MapEvent>(Channel.BUFFERED)
    val eventFlow: Flow<MapEvent> = events.receiveAsFlow()

    private val clock: Flow<Long> = flow {
        while (true) {
            emit(SystemClock.elapsedRealtimeNanos())
            delay(1_000L)
        }
    }

    /**
     * Every fix passes through the speed calculator and the navigation engine exactly once. This
     * flow is only collected via [uiState] (WhileSubscribed), so GPS stops ~5 s after the map
     * leaves the screen. (Phase 6 moves this into a foreground service for screen-off navigation.)
     */
    private val fixState: Flow<FixState> = fixes
        .map { fix ->
            latestFix.value = fix
            val speed = speedCalculator.update(fix)
            navigation.onFix(fix, speed)
            FixState(fix, speed)
        }
        .onStart { emit(FixState(null, null)) }

    private val gpsStatus: Flow<Pair<GpsStatus, FixState>> = combine(
        statusMonitor.permission,
        statusMonitor.locationEnabled,
        fixState,
        clock,
    ) { permission, enabled, state, now ->
        val fix = state.fix
        val status = when {
            permission == LocationPermission.NOT_GRANTED -> GpsStatus.NO_PERMISSION
            !enabled -> GpsStatus.LOCATION_OFF
            fix == null || fix.isCached -> GpsStatus.SEARCHING
            now - fix.elapsedRealtimeNanos > GPS_LOST_AFTER_NANOS -> GpsStatus.LOST
            (fix.accuracyMeters ?: Float.MAX_VALUE) > WEAK_ACCURACY_METERS -> GpsStatus.WEAK
            else -> GpsStatus.OK
        }
        status to state
    }

    private val locationPart = combine(
        statusMonitor.permission,
        statusMonitor.locationEnabled,
        connectivityMonitor.isOnline,
        gpsStatus,
        speedLimitProvider.speedLimits(fixes).onStart { emit(SpeedLimit.Unknown) },
    ) { permission, enabled, online, (gps, state), limit -> LocationPart(permission, enabled, online, gps, state, limit) }

    private val contentPart = combine(
        settingsRepository.settings,
        reportRepository.activeReports,
        destinationRepository.selected,
        roadName,
        navigation.state,
    ) { settings, reports, destination, road, nav -> ContentPart(settings, reports, destination, road, nav) }

    val uiState: StateFlow<MapUiState> = combine(locationPart, contentPart) { loc, content ->
        val usable = loc.gps == GpsStatus.OK || loc.gps == GpsStatus.WEAK
        val settings = content.settings
        val mps = loc.fixState.speedMps?.takeIf { usable }
        val speed = mps?.let { settings.speedUnit.fromMps(it).roundToInt() }
        val limitDisplay = (loc.limit as? SpeedLimit.Known)?.let { displayLimit(it.kmh, settings.speedUnit) }
        val fix = loc.fixState.fix
        MapUiState(
            permission = loc.permission,
            locationEnabled = loc.enabled,
            online = loc.online,
            gpsStatus = loc.gps,
            speed = speed,
            speedLimit = loc.limit,
            speedLimitDisplay = limitDisplay,
            isSpeeding = isSpeeding(speed, limitDisplay, settings.speedingTolerance),
            roadName = content.roadName.takeIf { usable },
            settings = settings,
            reports = content.reports,
            destination = content.destination,
            destinationDistanceMeters = content.destination?.let { d ->
                fix?.let { Geo.distanceMeters(it.latitude, it.longitude, d.latitude, d.longitude) }
            },
            navigation = content.navigation,
        )
    }
        .onEach(::maybeWarnSpeeding)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), MapUiState())

    init {
        viewModelScope.launch {
            navigation.events.collect { event ->
                when (event) {
                    is NavigationEvent.Arrived -> {
                        destinationRepository.clearSelection()
                        events.send(MapEvent.Message(context.getString(R.string.nav_arrived, event.destination.name)))
                    }
                }
            }
        }
    }

    private fun maybeWarnSpeeding(state: MapUiState) {
        val speak = speedingAlerter.update(state.isSpeeding, state.speedLimitDisplay, SystemClock.elapsedRealtime())
        if (speak && state.settings.speedingVoiceAlert) {
            voice.speak(context.getString(R.string.speeding_voice), VoiceAlertManager.Priority.HIGH)
        }
    }

    fun onRoadNameChanged(name: String?) {
        roadName.value = name
    }

    fun onPermissionResult() = statusMonitor.refreshPermission()

    fun toggleVoiceAlerts() {
        viewModelScope.launch {
            val enable = !settingsRepository.settings.value.voiceAlertsEnabled
            settingsRepository.setVoiceAlertsEnabled(enable)
            val text = context.getString(if (enable) R.string.voice_alerts_on else R.string.voice_alerts_off)
            if (enable) voice.speak(text, VoiceAlertManager.Priority.HIGH, force = true)
            events.send(MapEvent.Message(text))
        }
    }

    fun submitReport(type: ReportType) {
        viewModelScope.launch {
            // Only attach a report to a fresh, real fix — never to a stale or cached position.
            val fix = latestFix.value?.takeIf {
                !it.isCached && SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < GPS_LOST_AFTER_NANOS
            }
            val label = context.getString(type.style.label)
            val message = when (val result = reportRepository.submit(type, fix, roadName.value)) {
                is SubmitResult.Submitted -> {
                    voice.speak(context.getString(R.string.report_sent_voice, label))
                    context.getString(R.string.report_sent, label) +
                        if (result.shared) "" else "\n" + context.getString(R.string.report_local_only)
                }
                SubmitResult.Duplicate -> context.getString(R.string.report_duplicate)
                SubmitResult.TooSoon -> context.getString(R.string.report_too_fast)
                SubmitResult.NoLocation -> context.getString(R.string.report_no_location)
            }
            events.send(MapEvent.Message(message))
        }
    }

    // region destination & navigation

    fun clearDestination() {
        navigation.stop()
        destinationRepository.clearSelection()
    }

    fun routeToDestination() {
        uiState.value.destination?.let { navigation.preview(it) }
    }

    fun selectRoute(index: Int) = navigation.selectRoute(index)

    fun startNavigation() = navigation.start()

    /** Leaves the preview but keeps the destination pin. */
    fun cancelPreview() = navigation.stop()

    fun stopNavigation() {
        navigation.stop()
        destinationRepository.clearSelection()
    }

    fun setRouteOptions(options: RouteOptions) {
        viewModelScope.launch {
            settingsRepository.setRouteOptions(options)
            navigation.refreshPreview()
        }
    }

    fun saveFavorite(kind: FavoriteKind) {
        val place = uiState.value.destination ?: return
        viewModelScope.launch {
            destinationRepository.setFavorite(kind, place)
            message(if (kind == FavoriteKind.HOME) R.string.favorite_saved_home else R.string.favorite_saved_work)
        }
    }

    // endregion

    fun onVoiceTranscript(transcript: String) {
        when (val command = VoiceCommandParser.parse(transcript)) {
            is VoiceCommand.Report -> submitReport(command.type)
            is VoiceCommand.Search -> events.trySend(MapEvent.OpenSearch(command.query))
            is VoiceCommand.NavigateFavorite -> viewModelScope.launch {
                val kind = if (command.favorite == VoiceCommand.Favorite.HOME) FavoriteKind.HOME else FavoriteKind.WORK
                val place = destinationRepository.favorite(kind)
                if (place == null) {
                    message(if (kind == FavoriteKind.HOME) R.string.voice_no_favorite_home else R.string.voice_no_favorite_work)
                } else {
                    destinationRepository.select(place)
                    navigation.preview(place, startWhenReady = true)
                }
            }
            VoiceCommand.Cancel -> {
                clearDestination()
                message(R.string.voice_cancelled)
            }
            VoiceCommand.Repeat -> if (!voice.repeatLast()) message(R.string.voice_nothing_to_repeat)
            VoiceCommand.Unknown -> message(R.string.voice_not_understood)
        }
    }

    fun onVoiceUnavailable() = message(R.string.voice_unavailable)

    private fun message(res: Int) {
        events.trySend(MapEvent.Message(context.getString(res)))
    }

    private data class FixState(val fix: LocationFix?, val speedMps: Double?)

    private data class LocationPart(
        val permission: LocationPermission,
        val enabled: Boolean,
        val online: Boolean,
        val gps: GpsStatus,
        val fixState: FixState,
        val limit: SpeedLimit,
    )

    private data class ContentPart(
        val settings: UserSettings,
        val reports: List<TrafficReport>,
        val destination: Place?,
        val roadName: String?,
        val navigation: NavigationState,
    )

    companion object {
        private const val GPS_LOST_AFTER_NANOS = 6_000_000_000L
        private const val WEAK_ACCURACY_METERS = 30f

        /** Limits are posted in km/h in OSM for NL; mph users see the nearest 5 mph. */
        fun displayLimit(kmh: Int, unit: SpeedUnit): Int =
            if (unit == SpeedUnit.KMH) kmh else ((kmh / 1.609344) / 5).roundToInt() * 5

        /** Tolerance 0 = any speed above the limit; otherwise speed ≥ limit + tolerance. */
        fun isSpeeding(speed: Int?, limit: Int?, tolerance: Int): Boolean {
            if (speed == null || limit == null) return false
            return if (tolerance <= 0) speed > limit else speed >= limit + tolerance
        }
    }
}
