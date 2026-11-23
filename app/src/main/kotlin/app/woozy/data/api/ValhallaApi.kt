package app.woozy.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Valhalla routing engine (https://github.com/valhalla/valhalla).
 * Default endpoint: the FOSSGIS public instance — fine for development, NOT for production
 * traffic. Point ROUTING_BASE_URL at a self-hosted Valhalla (free) or a commercial host.
 */
interface ValhallaApi {
    @POST("route")
    suspend fun route(@Body request: ValhallaRouteRequest): ValhallaRouteResponse

    @POST("trace_attributes")
    suspend fun traceAttributes(@Body request: ValhallaTraceRequest): ValhallaTraceResponse
}

// region requests

@Serializable
data class ValhallaLocation(
    val lat: Double,
    val lon: Double,
    val type: String = "break",
    /** Direction of travel at the origin; keeps reroutes from starting with a U-turn. */
    val heading: Int? = null,
    @SerialName("heading_tolerance") val headingTolerance: Int? = null,
)

@Serializable
data class ValhallaRouteRequest(
    val locations: List<ValhallaLocation>,
    val costing: String = "auto",
    @SerialName("costing_options") val costingOptions: ValhallaCostingOptions,
    val units: String = "kilometers",
    val language: String = "en-GB",
    val alternates: Int = 0,
    @SerialName("directions_type") val directionsType: String = "instructions",
)

@Serializable
data class ValhallaCostingOptions(val auto: ValhallaAutoCosting)

@Serializable
data class ValhallaAutoCosting(
    val shortest: Boolean = false,
    /** 0 = avoid, 0.5 = neutral, 1 = prefer. */
    @SerialName("use_tolls") val useTolls: Double = 0.5,
    @SerialName("use_highways") val useHighways: Double = 1.0,
    @SerialName("use_ferry") val useFerry: Double = 0.5,
)

@Serializable
data class ValhallaTraceRequest(
    @SerialName("encoded_polyline") val encodedPolyline: String? = null,
    val shape: List<ValhallaShapePoint>? = null,
    /** "edge_walk" for a shape that is exactly on the graph (our own route), "map_snap" for GPS. */
    @SerialName("shape_match") val shapeMatch: String,
    val costing: String = "auto",
    val filters: ValhallaFilters,
)

@Serializable
data class ValhallaShapePoint(val lat: Double, val lon: Double)

@Serializable
data class ValhallaFilters(val attributes: List<String>, val action: String = "include")

// endregion

// region responses

@Serializable
data class ValhallaRouteResponse(
    val trip: ValhallaTrip,
    val alternates: List<ValhallaAlternate> = emptyList(),
)

@Serializable
data class ValhallaAlternate(val trip: ValhallaTrip)

@Serializable
data class ValhallaTrip(
    val legs: List<ValhallaLeg> = emptyList(),
    val summary: ValhallaSummary,
)

@Serializable
data class ValhallaLeg(
    val shape: String,
    val maneuvers: List<ValhallaManeuver> = emptyList(),
    val summary: ValhallaSummary,
)

@Serializable
data class ValhallaSummary(
    /** Kilometres (we always request kilometres). */
    val length: Double,
    /** Seconds. */
    val time: Double,
    @SerialName("has_toll") val hasToll: Boolean = false,
    @SerialName("has_highway") val hasHighway: Boolean = false,
    @SerialName("has_ferry") val hasFerry: Boolean = false,
)

@Serializable
data class ValhallaManeuver(
    val type: Int,
    val instruction: String = "",
    @SerialName("verbal_transition_alert_instruction") val verbalAlert: String? = null,
    @SerialName("verbal_pre_transition_instruction") val verbalPre: String? = null,
    @SerialName("verbal_post_transition_instruction") val verbalPost: String? = null,
    @SerialName("street_names") val streetNames: List<String>? = null,
    val length: Double = 0.0,
    val time: Double = 0.0,
    @SerialName("begin_shape_index") val beginShapeIndex: Int = 0,
    @SerialName("end_shape_index") val endShapeIndex: Int = 0,
    @SerialName("roundabout_exit_count") val roundaboutExitCount: Int? = null,
)

@Serializable
data class ValhallaTraceResponse(
    val edges: List<ValhallaEdge> = emptyList(),
    @SerialName("matched_points") val matchedPoints: List<ValhallaMatchedPoint> = emptyList(),
)

@Serializable
data class ValhallaEdge(
    /** km/h; absent or 0 when unknown. 255 means "no limit" (e.g. parts of the Autobahn). */
    @SerialName("speed_limit") val speedLimit: Int? = null,
    val names: List<String>? = null,
    val length: Double? = null,
    @SerialName("begin_shape_index") val beginShapeIndex: Int? = null,
    @SerialName("end_shape_index") val endShapeIndex: Int? = null,
)

@Serializable
data class ValhallaMatchedPoint(
    val type: String? = null,
    @SerialName("edge_index") val edgeIndex: Int? = null,
    @SerialName("distance_from_trace_point") val distanceFromTracePoint: Double? = null,
)

/** Error body returned with HTTP 400, e.g. error_code 442 "No path could be found for input". */
@Serializable
data class ValhallaError(
    @SerialName("error_code") val errorCode: Int? = null,
    val error: String? = null,
)

// endregion
