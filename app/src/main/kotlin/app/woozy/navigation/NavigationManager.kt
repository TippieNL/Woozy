package app.woozy.navigation

import android.os.SystemClock
import android.util.Log
import app.woozy.data.providers.RoutingException
import app.woozy.data.providers.RoutingProvider
import app.woozy.data.repository.SettingsRepository
import app.woozy.di.AppScope
import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.Place
import app.woozy.domain.model.Route
import app.woozy.domain.model.SpeedLimit
import app.woozy.domain.model.SpeedLimitSpan
import app.woozy.domain.model.SpeedUnit
import app.woozy.location.LocationRepository
import app.woozy.services.VoiceAlertManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class RouteError { NO_LOCATION, NETWORK, NO_ROUTE, SERVER }

sealed interface NavigationState {
    data object Idle : NavigationState

    data class Previewing(
        val destination: Place,
        val routes: List<Route> = emptyList(),
        val selected: Int = 0,
        val loading: Boolean = true,
        val error: RouteError? = null,
    ) : NavigationState {
        val selectedRoute: Route? get() = routes.getOrNull(selected)
    }

    data class Navigating(
        val destination: Place,
        val route: Route,
        val progress: RouteProgress? = null,
        val rerouting: Boolean = false,
        /** Null while loading or when the provider has no data. */
        val speedLimits: List<SpeedLimitSpan>? = null,
    ) : NavigationState
}

sealed interface NavigationEvent {
    data class Arrived(val destination: Place) : NavigationEvent
}

/**
 * Owns the navigation session: route preview with alternatives, active guidance, progress,
 * off-route rerouting, spoken instructions and arrival.
 *
 * Fed by [onFix]; all state changes happen on the main thread. GPS ownership stays with whoever
 * calls [onFix] (the map today, the foreground service in Phase 6).
 */
@Singleton
class NavigationManager @Inject constructor(
    private val routing: RoutingProvider,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val voice: VoiceAlertManager,
    @AppScope appScope: CoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<NavigationEvent> = _events.asSharedFlow()

    private var tracker: RouteTracker? = null
    private val guidance = VoiceGuidance { settingsRepository.settings.value.speedUnit == SpeedUnit.MPH }
    private var routeJob: Job? = null
    private var speedLimitJob: Job? = null
    private var lastRerouteAt = 0L
    private var spanHint = 0

    // region preview

    /** Calculates routes to [destination]. With [startWhenReady] guidance begins on success (voice "navigate home"). */
    fun preview(destination: Place, startWhenReady: Boolean = false) {
        cancelJobs()
        tracker = null
        _state.value = NavigationState.Previewing(destination)
        routeJob = scope.launch {
            val fix = latestFix()
            if (fix == null) {
                _state.value = NavigationState.Previewing(destination, loading = false, error = RouteError.NO_LOCATION)
                return@launch
            }
            try {
                val routes = routing.route(
                    origin = GeoPoint(fix.latitude, fix.longitude),
                    destination = GeoPoint(destination.latitude, destination.longitude),
                    options = settingsRepository.settings.value.routeOptions,
                    originHeading = fix.headingIfMoving(),
                )
                _state.value = NavigationState.Previewing(destination, routes, selected = 0, loading = false)
                if (startWhenReady) start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Route preview failed", e)
                _state.value = NavigationState.Previewing(destination, loading = false, error = e.toRouteError())
            }
        }
    }

    fun selectRoute(index: Int) {
        val p = _state.value as? NavigationState.Previewing ?: return
        if (index in p.routes.indices) _state.value = p.copy(selected = index)
    }

    /** Re-run the preview (e.g. after changing route options). */
    fun refreshPreview() {
        (_state.value as? NavigationState.Previewing)?.let { preview(it.destination) }
    }

    // endregion

    fun start() {
        val p = _state.value as? NavigationState.Previewing ?: return
        val route = p.selectedRoute ?: return
        beginRoute(p.destination, route, announce = true)
    }

    fun stop() {
        cancelJobs()
        tracker = null
        guidance.reset()
        _state.value = NavigationState.Idle
    }

    /** Process a GPS fix. Call on the main thread. */
    fun onFix(fix: LocationFix, speedMps: Double?) {
        if (fix.isCached) return
        val nav = _state.value as? NavigationState.Navigating ?: return
        val tracker = tracker ?: return
        val progress = tracker.update(fix) ?: return

        if (progress.remainingDistance < ARRIVAL_METERS && progress.distanceFromRoute < 60) {
            arrive(nav)
            return
        }
        if (progress.offRoute) reroute(nav, fix)

        _state.value = nav.copy(progress = progress)

        val settings = settingsRepository.settings.value
        if (settings.voiceGuidance && !progress.offRoute && !nav.rerouting) {
            guidance.next(nav.route, tracker.maneuverStart, progress, speedMps ?: 0.0)?.let {
                voice.speak(it, VoiceAlertManager.Priority.HIGH, dedupe = false)
            }
        }
    }

    /** Posted limit at the current route position; null when not navigating on-route (use map matching instead). */
    fun routeSpeedLimit(): SpeedLimit? {
        val nav = _state.value as? NavigationState.Navigating ?: return null
        val spans = nav.speedLimits ?: return null
        val progress = nav.progress ?: return null
        if (progress.offRoute || nav.rerouting) return null
        val (segment, _) = tracker?.geometry?.pointAt(progress.distanceAlong) ?: return null
        val index = findSpan(spans, segment) ?: return SpeedLimit.Unknown
        return spans[index].kmh?.let { SpeedLimit.Known(it, SOURCE) } ?: SpeedLimit.Unknown
    }

    private fun findSpan(spans: List<SpeedLimitSpan>, segment: Int): Int? {
        // Search from the last hit: progress is monotonic almost always.
        val start = spanHint.coerceIn(0, spans.lastIndex.coerceAtLeast(0))
        for (i in start until spans.size) if (segment >= spans[i].beginShapeIndex && segment < spans[i].endShapeIndex) return i.also { spanHint = it }
        for (i in 0 until start) if (segment >= spans[i].beginShapeIndex && segment < spans[i].endShapeIndex) return i.also { spanHint = it }
        return null
    }

    private fun beginRoute(destination: Place, route: Route, announce: Boolean) {
        routeJob?.cancel()
        val tracker = RouteTracker(route)
        this.tracker = tracker
        guidance.reset()
        spanHint = 0
        _state.value = NavigationState.Navigating(destination, route)
        // Always prime guidance (so a reroute doesn't re-announce what's right ahead); speak only on a fresh start.
        val opening = guidance.start(route, tracker.maneuverStart)
        if (announce && opening != null && settingsRepository.settings.value.voiceGuidance) {
            voice.speak(opening, VoiceAlertManager.Priority.HIGH, dedupe = false)
        }
        speedLimitJob?.cancel()
        speedLimitJob = scope.launch {
            val spans = try {
                routing.speedLimitsAlong(route)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Speed limits along route unavailable", e)
                null
            }
            val current = _state.value as? NavigationState.Navigating
            if (current != null && current.route === route) _state.value = current.copy(speedLimits = spans)
        }
    }

    private fun reroute(nav: NavigationState.Navigating, fix: LocationFix) {
        val now = SystemClock.elapsedRealtime()
        if (routeJob?.isActive == true || now - lastRerouteAt < REROUTE_INTERVAL_MS) return
        lastRerouteAt = now
        _state.value = nav.copy(rerouting = true)
        routeJob = scope.launch {
            try {
                val route = routing.route(
                    origin = GeoPoint(fix.latitude, fix.longitude),
                    destination = GeoPoint(nav.destination.latitude, nav.destination.longitude),
                    options = settingsRepository.settings.value.routeOptions,
                    originHeading = fix.headingIfMoving(),
                    alternatives = 0,
                ).first()
                beginRoute(nav.destination, route, announce = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reroute failed; will retry", e)
                (_state.value as? NavigationState.Navigating)?.let { _state.value = it.copy(rerouting = false) }
            }
        }
    }

    private fun arrive(nav: NavigationState.Navigating) {
        val final = nav.route.maneuvers.lastOrNull()
        // Guidance normally already said it ~7 s earlier; only speak if it didn't.
        val alreadySaid = voice.lastAnnouncement == final?.verbalPre
        if (!alreadySaid && settingsRepository.settings.value.voiceGuidance) {
            final?.verbalPre?.let { voice.speak(it, VoiceAlertManager.Priority.HIGH, dedupe = false) }
        }
        stop()
        _events.tryEmit(NavigationEvent.Arrived(nav.destination))
    }

    private fun cancelJobs() {
        routeJob?.cancel()
        speedLimitJob?.cancel()
    }

    private fun latestFix(): LocationFix? = locationRepository.fixes.replayCache.lastOrNull()

    private fun LocationFix.headingIfMoving(): Float? = bearingDegrees?.takeIf { (speedMps ?: 0f) > 3f }

    private fun Exception.toRouteError() = when (this) {
        is RoutingException.NoRoute -> RouteError.NO_ROUTE
        is RoutingException.Network -> RouteError.NETWORK
        else -> RouteError.SERVER
    }

    private companion object {
        const val TAG = "NavigationManager"
        const val ARRIVAL_METERS = 25.0
        const val REROUTE_INTERVAL_MS = 8_000L
        const val SOURCE = "OpenStreetMap maxspeed via Valhalla"
    }
}
