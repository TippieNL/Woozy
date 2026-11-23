package app.woozy.navigation

import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.Route

data class RouteProgress(
    val distanceAlong: Double,
    val distanceFromRoute: Double,
    val snappedPoint: GeoPoint,
    /** Index of the next maneuver to perform; null once the last one is behind us. */
    val nextManeuverIndex: Int?,
    val distanceToNextManeuver: Double,
    val remainingDistance: Double,
    val remainingSeconds: Double,
    val currentStreet: String?,
    val offRoute: Boolean,
)

/**
 * Tracks the user's position along one route: snapping, maneuver lookup, remaining time and
 * off-route detection. One instance per route; not thread-safe.
 */
class RouteTracker(val route: Route) {
    val geometry = RouteGeometry(route.points)

    /** Distance along the route where each maneuver starts. */
    val maneuverStart: DoubleArray = DoubleArray(route.maneuvers.size) { i ->
        geometry.cumulative[route.maneuvers[i].beginShapeIndex.coerceIn(0, geometry.cumulative.lastIndex)]
    }

    private var lastSegment = -1
    private var offRouteStreak = 0

    fun update(fix: LocationFix): RouteProgress? {
        val position = GeoPoint(fix.latitude, fix.longitude)
        val snap = geometry.snap(position, lastSegment) ?: return null
        lastSegment = snap.segmentIndex

        val accuracy = (fix.accuracyMeters ?: 30f).toDouble()
        val threshold = OFF_ROUTE_BASE_M + accuracy.coerceAtMost(50.0)
        // Only trust "off route" from reasonably accurate fixes, and require a streak to ride out noise.
        offRouteStreak = when {
            accuracy > 60 -> offRouteStreak
            snap.distanceFromRoute > threshold -> offRouteStreak + 1
            else -> 0
        }
        return progressAt(snap.distanceAlong, snap.distanceFromRoute, snap.point, offRouteStreak >= OFF_ROUTE_STREAK)
    }

    fun progressAt(along: Double, fromRoute: Double = 0.0, point: GeoPoint = geometry.pointAt(along).second, offRoute: Boolean = false): RouteProgress {
        val maneuvers = route.maneuvers
        var next: Int? = null
        for (i in 1 until maneuvers.size) {
            if (maneuverStart[i] > along + 1.0) {
                next = i
                break
            }
        }
        val current = (next ?: maneuvers.size).minus(1).coerceAtLeast(0)
        val remainingSeconds = if (maneuvers.isEmpty()) 0.0 else {
            val segStart = maneuverStart[current]
            val segEnd = next?.let { maneuverStart[it] } ?: geometry.length
            val fraction = if (segEnd - segStart <= 0) 0.0 else ((segEnd - along) / (segEnd - segStart)).coerceIn(0.0, 1.0)
            var t = fraction * maneuvers[current].durationSeconds
            if (next != null) for (j in next until maneuvers.size) t += maneuvers[j].durationSeconds
            t
        }
        return RouteProgress(
            distanceAlong = along,
            distanceFromRoute = fromRoute,
            snappedPoint = point,
            nextManeuverIndex = next,
            distanceToNextManeuver = next?.let { maneuverStart[it] - along } ?: (geometry.length - along),
            remainingDistance = (geometry.length - along).coerceAtLeast(0.0),
            remainingSeconds = remainingSeconds,
            currentStreet = maneuvers.getOrNull(current)?.streetNames?.firstOrNull(),
            offRoute = offRoute,
        )
    }

    private companion object {
        const val OFF_ROUTE_BASE_M = 30.0
        const val OFF_ROUTE_STREAK = 3
    }
}
