package app.woozy.domain.model

enum class RouteType { FASTEST, SHORTEST }

data class RouteOptions(
    val type: RouteType = RouteType.FASTEST,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
)

/** Maneuver categories the UI and voice guidance care about (mapped from Valhalla's types). */
enum class ManeuverKind {
    DEPART, ARRIVE, STRAIGHT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT,
    UTURN, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, RAMP_RIGHT, RAMP_LEFT, KEEP_RIGHT, KEEP_LEFT,
    MERGE, FERRY,
}

data class Maneuver(
    val kind: ManeuverKind,
    val instruction: String,
    /** Spoken well before the maneuver ("Turn right onto X."); may be null for trivial maneuvers. */
    val verbalAlert: String?,
    /** Spoken right before the maneuver. */
    val verbalPre: String?,
    /** Spoken after the maneuver ("Continue for 3 kilometres."). */
    val verbalPost: String?,
    val streetNames: List<String>,
    val lengthMeters: Double,
    val durationSeconds: Double,
    /** Index into [Route.points] where this maneuver happens. */
    val beginShapeIndex: Int,
    val endShapeIndex: Int,
    val roundaboutExit: Int?,
)

data class Route(
    val points: List<GeoPoint>,
    val lengthMeters: Double,
    val durationSeconds: Double,
    val maneuvers: List<Maneuver>,
    val hasToll: Boolean,
    val hasHighway: Boolean,
    val hasFerry: Boolean,
    /** Opaque encoded shape, reused for follow-up requests (speed limits). */
    val encodedShape: String,
)

/** Posted limit over a stretch of route, by shape index. kmh == null means no data. */
data class SpeedLimitSpan(val beginShapeIndex: Int, val endShapeIndex: Int, val kmh: Int?)
