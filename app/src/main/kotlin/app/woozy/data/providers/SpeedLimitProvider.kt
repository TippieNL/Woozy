package app.woozy.data.providers

import android.os.SystemClock
import android.util.Log
import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.SpeedLimit
import app.woozy.navigation.NavigationManager
import app.woozy.util.Geo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the posted speed limit for the user's current road. Implementations must never guess
 * from road type: a wrong limit is worse than "--".
 */
interface SpeedLimitProvider {
    fun speedLimits(fixes: Flow<LocationFix>): Flow<SpeedLimit>
}

/**
 * OSM `maxspeed` via Valhalla:
 *  - while navigating on-route: per-edge limits fetched once for the whole route (no extra calls);
 *  - otherwise ("free drive"): map-matches the last few GPS points at most every [REQUEST_INTERVAL_MS]
 *    while moving, and holds the result for a short distance/time.
 * When nothing reliable is known the result is [SpeedLimit.Unknown].
 */
@Singleton
class ValhallaSpeedLimitProvider @Inject constructor(
    private val routing: RoutingProvider,
    private val navigation: NavigationManager,
) : SpeedLimitProvider {

    private data class Cached(val limit: SpeedLimit, val at: GeoPoint, val elapsedMs: Long)

    override fun speedLimits(fixes: Flow<LocationFix>): Flow<SpeedLimit> = channelFlow {
        val trail = ArrayDeque<LocationFix>()
        var cached: Cached? = null
        var inFlight: Job? = null
        var lastRequestAt = 0L
        var lastRequestPos: GeoPoint? = null

        fixes.collect { fix ->
            if (fix.isCached) return@collect
            navigation.routeSpeedLimit()?.let {
                send(it)
                return@collect
            }

            val here = GeoPoint(fix.latitude, fix.longitude)
            val now = SystemClock.elapsedRealtime()
            val prev = trail.lastOrNull()
            if (prev == null || Geo.distanceMeters(prev.latitude, prev.longitude, fix.latitude, fix.longitude) >= TRAIL_SPACING_M) {
                trail.addLast(fix)
                while (trail.size > TRAIL_POINTS) trail.removeFirst()
            }
            while (trail.isNotEmpty() && (fix.elapsedRealtimeNanos - trail.first().elapsedRealtimeNanos) > TRAIL_MAX_AGE_NANOS) {
                trail.removeFirst()
            }

            val c = cached
            val valid = c != null && now - c.elapsedMs < HOLD_MS &&
                Geo.distanceMeters(c.at.latitude, c.at.longitude, here.latitude, here.longitude) < HOLD_DISTANCE_M
            send(if (valid) c.limit else SpeedLimit.Unknown)

            val moving = (fix.speedMps ?: 0f) > MIN_SPEED_MPS
            val movedEnough = lastRequestPos?.let { Geo.distanceMeters(it.latitude, it.longitude, here.latitude, here.longitude) >= MIN_MOVE_M } ?: true
            val due = moving && trail.size >= 2 && inFlight?.isActive != true &&
                now - lastRequestAt >= REQUEST_INTERVAL_MS && movedEnough
            if (due) {
                lastRequestAt = now
                lastRequestPos = here
                val points = trail.map { GeoPoint(it.latitude, it.longitude) }
                inFlight = launch {
                    val kmh = try {
                        routing.speedLimitAt(points)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d(TAG, "Speed limit lookup failed: ${e.message}")
                        return@launch // keep the previous value until it expires
                    }
                    val limit = kmh?.let { SpeedLimit.Known(it, SOURCE) } ?: SpeedLimit.Unknown
                    cached = Cached(limit, points.last(), SystemClock.elapsedRealtime())
                    send(limit)
                }
            }
        }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "SpeedLimitProvider"
        const val SOURCE = "OpenStreetMap maxspeed via Valhalla"
        const val TRAIL_POINTS = 6
        const val TRAIL_SPACING_M = 15.0
        const val TRAIL_MAX_AGE_NANOS = 30_000_000_000L
        const val REQUEST_INTERVAL_MS = 8_000L
        const val MIN_MOVE_M = 60.0
        const val MIN_SPEED_MPS = 2.5f
        const val HOLD_MS = 45_000L
        const val HOLD_DISTANCE_M = 400.0
    }
}
