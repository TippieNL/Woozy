package app.woozy.navigation

import app.woozy.domain.model.GeoPoint
import app.woozy.util.Geo
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Precomputed geometry for one route: cumulative distances and fast snapping of a GPS position
 * onto the polyline. Snapping searches forward from the previous position first, so a route that
 * passes the same road twice (ring roads, loops) doesn't make progress jump.
 */
class RouteGeometry(val points: List<GeoPoint>) {

    /** cumulative[i] = metres along the route at points[i]. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            c[i] = c[i - 1] + Geo.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }

    val length: Double get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    data class Snap(
        /** Index of the segment start point (segment = points[i]..points[i+1]). */
        val segmentIndex: Int,
        val distanceAlong: Double,
        val distanceFromRoute: Double,
        val point: GeoPoint,
        /** Bearing of the route segment, degrees. */
        val segmentBearing: Double,
    )

    /**
     * @param hintSegment segment of the previous snap, or -1 for a full search.
     * @param lookAheadMeters how far beyond the hint to search before falling back to a full search.
     */
    fun snap(position: GeoPoint, hintSegment: Int = -1, lookAheadMeters: Double = 1_500.0, acceptMeters: Double = 60.0): Snap? {
        if (points.size < 2) return null
        if (hintSegment >= 0) {
            val start = (hintSegment - 2).coerceAtLeast(0)
            val limit = cumulative[start] + lookAheadMeters
            var end = start
            while (end < points.size - 2 && cumulative[end] < limit) end++
            val local = bestIn(position, start, end)
            if (local != null && local.distanceFromRoute <= acceptMeters) return local
        }
        return bestIn(position, 0, points.size - 2)
    }

    /** Point and segment index at a given distance along the route. */
    fun pointAt(distance: Double): Pair<Int, GeoPoint> {
        if (distance <= 0) return 0 to points.first()
        if (distance >= length) return (points.size - 2).coerceAtLeast(0) to points.last()
        var lo = 0
        var hi = cumulative.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] <= distance) lo = mid else hi = mid
        }
        val segLen = cumulative[hi] - cumulative[lo]
        val t = if (segLen <= 0) 0.0 else (distance - cumulative[lo]) / segLen
        val a = points[lo]
        val b = points[hi]
        return lo to GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }

    /** Remaining polyline from [distance] to the end (for drawing the route ahead). */
    fun remainingFrom(distance: Double): List<GeoPoint> {
        val (segment, point) = pointAt(distance)
        val out = ArrayList<GeoPoint>(points.size - segment)
        out += point
        for (i in segment + 1 until points.size) out += points[i]
        return out
    }

    private fun bestIn(p: GeoPoint, from: Int, to: Int): Snap? {
        var best: Snap? = null
        val cosLat = cos(Math.toRadians(p.latitude))
        for (i in from..to) {
            val a = points[i]
            val b = points[i + 1]
            // Local equirectangular projection around p, metres.
            val ax = (a.longitude - p.longitude) * cosLat * M_PER_DEG
            val ay = (a.latitude - p.latitude) * M_PER_DEG
            val bx = (b.longitude - p.longitude) * cosLat * M_PER_DEG
            val by = (b.latitude - p.latitude) * M_PER_DEG
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (best == null || d < best.distanceFromRoute) {
                val segLen = cumulative[i + 1] - cumulative[i]
                best = Snap(
                    segmentIndex = i,
                    distanceAlong = cumulative[i] + t * segLen,
                    distanceFromRoute = d,
                    point = GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t),
                    segmentBearing = Geo.bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude),
                )
            }
        }
        return best
    }

    private companion object {
        const val M_PER_DEG = 111_195.0
    }
}
