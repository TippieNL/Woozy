package app.woozy.util

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from point 1 to point 2, degrees 0..360 (0 = north). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Lat/lon bounding box of a circle; good enough for SQL pre-filtering. */
    fun boundingBox(lat: Double, lon: Double, radiusMeters: Double): BoundingBox {
        val dLat = Math.toDegrees(radiusMeters / EARTH_RADIUS_M)
        val dLon = Math.toDegrees(radiusMeters / (EARTH_RADIUS_M * cos(Math.toRadians(lat)).coerceAtLeast(0.01)))
        return BoundingBox(lat - dLat, lat + dLat, lon - dLon, lon + dLon)
    }

    data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)
}

/** "350 m", "1.2 km", "12 km" (metric) or "0.2 mi" / "12 mi" (imperial). */
fun formatDistance(meters: Double, imperial: Boolean): String = if (imperial) {
    val miles = meters / 1609.344
    when {
        miles < 0.1 -> "${(meters * 3.28084 / 10).toInt() * 10} ft"
        miles < 10 -> "%.1f mi".format(miles)
        else -> "${miles.toInt()} mi"
    }
} else {
    when {
        meters < 1000 -> "${((meters / 10).toInt() * 10).coerceAtLeast(0)} m"
        meters < 10_000 -> "%.1f km".format(meters / 1000)
        else -> "${(meters / 1000).toInt()} km"
    }
}
