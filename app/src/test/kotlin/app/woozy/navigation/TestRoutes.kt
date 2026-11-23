package app.woozy.navigation

import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.Maneuver
import app.woozy.domain.model.ManeuverKind
import app.woozy.domain.model.Route

/** Straight route due east along latitude 52.0, ~1 km per 0.01462° of longitude, with an L-turn north. */
object TestRoutes {
    private const val LAT = 52.0
    private const val DEG_PER_KM_LON = 0.014616

    /** 0 → 2 km east, then 1 km north. Maneuvers: depart, turn left at 2 km, arrive at 3 km. */
    val lRoute: Route by lazy {
        val pts = ArrayList<GeoPoint>()
        for (i in 0..20) pts += GeoPoint(LAT, 5.0 + i * 0.1 * DEG_PER_KM_LON) // every 100 m
        val turnLon = pts.last().longitude
        for (i in 1..10) pts += GeoPoint(LAT + i * 0.1 / 111.195, turnLon) // every 100 m north
        Route(
            points = pts,
            lengthMeters = 3000.0,
            durationSeconds = 300.0,
            maneuvers = listOf(
                maneuver(ManeuverKind.DEPART, "Drive east on A Street.", 0, 20, 2000.0, 200.0, streets = listOf("A Street"), post = "Continue for 2 kilometres."),
                maneuver(ManeuverKind.LEFT, "Turn left onto B Street.", 20, 30, 1000.0, 100.0, streets = listOf("B Street"), alert = "Turn left onto B Street.", pre = "Turn left onto B Street."),
                maneuver(ManeuverKind.ARRIVE, "You have arrived at your destination.", 30, 30, 0.0, 0.0, pre = "You have arrived at your destination."),
            ),
            hasToll = false,
            hasHighway = false,
            hasFerry = false,
            encodedShape = PolylineCodec.encode(pts),
        )
    }

    fun eastOfStart(meters: Double, offsetNorthMeters: Double = 0.0) =
        fix(LAT + offsetNorthMeters / 111_195.0, 5.0 + meters / 1000.0 * DEG_PER_KM_LON)

    fun fix(lat: Double, lon: Double, accuracy: Float = 5f) = LocationFix(
        latitude = lat, longitude = lon, accuracyMeters = accuracy, speedMps = 14f, speedAccuracyMps = 0.5f,
        bearingDegrees = 90f, bearingAccuracyDegrees = 5f, altitudeMeters = null, timeMillis = 0L,
        elapsedRealtimeNanos = 0L, provider = "test",
    )

    private fun maneuver(
        kind: ManeuverKind, instruction: String, begin: Int, end: Int, length: Double, time: Double,
        streets: List<String> = emptyList(), alert: String? = null, pre: String? = instruction, post: String? = null,
    ) = Maneuver(kind, instruction, alert, pre, post, streets, length, time, begin, end, null)
}
