package app.woozy.data.providers

import app.woozy.data.api.ValhallaApi
import app.woozy.data.api.ValhallaAutoCosting
import app.woozy.data.api.ValhallaCostingOptions
import app.woozy.data.api.ValhallaError
import app.woozy.data.api.ValhallaFilters
import app.woozy.data.api.ValhallaLocation
import app.woozy.data.api.ValhallaManeuver
import app.woozy.data.api.ValhallaRouteRequest
import app.woozy.data.api.ValhallaShapePoint
import app.woozy.data.api.ValhallaTraceRequest
import app.woozy.data.api.ValhallaTrip
import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.Maneuver
import app.woozy.domain.model.ManeuverKind
import app.woozy.domain.model.Route
import app.woozy.domain.model.RouteOptions
import app.woozy.domain.model.RouteType
import app.woozy.domain.model.SpeedLimitSpan
import app.woozy.navigation.PolylineCodec
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

sealed class RoutingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoRoute(message: String) : RoutingException(message)
    class Network(cause: Throwable) : RoutingException("Routing server unreachable", cause)
    class Server(message: String, cause: Throwable? = null) : RoutingException(message, cause)
}

/** Route calculation and road attributes. Swap the binding in di/AppModule to change engine. */
interface RoutingProvider {
    /** @return main route first, then alternatives. Throws [RoutingException]. */
    suspend fun route(
        origin: GeoPoint,
        destination: GeoPoint,
        options: RouteOptions,
        originHeading: Float? = null,
        alternatives: Int = 2,
    ): List<Route>

    /** Posted speed limits along a route calculated by this provider. */
    suspend fun speedLimitsAlong(route: Route): List<SpeedLimitSpan>

    /**
     * Map-matches a short GPS trail and returns the limit (km/h) of the road at its last point,
     * or null when the road has no limit data or matching failed.
     */
    suspend fun speedLimitAt(trail: List<GeoPoint>): Int?
}

@Singleton
class ValhallaRoutingProvider @Inject constructor(
    private val api: ValhallaApi,
    private val json: Json,
) : RoutingProvider {

    override suspend fun route(
        origin: GeoPoint,
        destination: GeoPoint,
        options: RouteOptions,
        originHeading: Float?,
        alternatives: Int,
    ): List<Route> {
        val request = ValhallaRouteRequest(
            locations = listOf(
                ValhallaLocation(
                    lat = origin.latitude,
                    lon = origin.longitude,
                    heading = originHeading?.roundToInt()?.mod(360),
                    headingTolerance = originHeading?.let { 45 },
                ),
                ValhallaLocation(destination.latitude, destination.longitude),
            ),
            costingOptions = ValhallaCostingOptions(
                ValhallaAutoCosting(
                    shortest = options.type == RouteType.SHORTEST,
                    useTolls = if (options.avoidTolls) 0.0 else 0.5,
                    useHighways = if (options.avoidHighways) 0.0 else 1.0,
                    useFerry = if (options.avoidFerries) 0.0 else 0.5,
                ),
            ),
            alternates = alternatives,
        )
        val response = call { api.route(request) }
        return (listOf(response.trip) + response.alternates.map { it.trip }).mapNotNull { it.toRoute() }
            .ifEmpty { throw RoutingException.NoRoute("Empty route") }
    }

    override suspend fun speedLimitsAlong(route: Route): List<SpeedLimitSpan> {
        val response = call {
            api.traceAttributes(
                ValhallaTraceRequest(
                    encodedPolyline = route.encodedShape,
                    shapeMatch = "edge_walk",
                    filters = ValhallaFilters(listOf("edge.speed_limit", "edge.begin_shape_index", "edge.end_shape_index")),
                ),
            )
        }
        return response.edges.mapNotNull { edge ->
            val begin = edge.beginShapeIndex ?: return@mapNotNull null
            val end = edge.endShapeIndex ?: return@mapNotNull null
            SpeedLimitSpan(begin, end, edge.speedLimit.toPostedLimit())
        }
    }

    override suspend fun speedLimitAt(trail: List<GeoPoint>): Int? {
        if (trail.size < 2) return null
        val response = call {
            api.traceAttributes(
                ValhallaTraceRequest(
                    shape = trail.map { ValhallaShapePoint(it.latitude, it.longitude) },
                    shapeMatch = "map_snap",
                    filters = ValhallaFilters(listOf("edge.speed_limit", "matched.edge_index", "matched.type")),
                ),
            )
        }
        val last = response.matchedPoints.lastOrNull { it.type == "matched" && it.edgeIndex != null } ?: return null
        return response.edges.getOrNull(last.edgeIndex!!)?.speedLimit.toPostedLimit()
    }

    /** 0/absent = unknown; 255 = unrestricted, which a round sign can't show either. */
    private fun Int?.toPostedLimit(): Int? = this?.takeIf { it in 5..200 }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        val error = body?.let { runCatching { json.decodeFromString(ValhallaError.serializer(), it) }.getOrNull() }
        if (e.code() == 400) throw RoutingException.NoRoute(error?.error ?: "No route found")
        throw RoutingException.Server(error?.error ?: "Routing server error ${e.code()}", e)
    } catch (e: IOException) {
        throw RoutingException.Network(e)
    }

    private fun ValhallaTrip.toRoute(): Route? {
        // We only request single-leg (A→B) routes.
        val leg = legs.firstOrNull() ?: return null
        val points = PolylineCodec.decode(leg.shape, precision = 6)
        if (points.size < 2) return null
        return Route(
            points = points,
            lengthMeters = summary.length * 1000,
            durationSeconds = summary.time,
            maneuvers = leg.maneuvers.map { it.toManeuver(points.lastIndex) },
            hasToll = summary.hasToll,
            hasHighway = summary.hasHighway,
            hasFerry = summary.hasFerry,
            encodedShape = leg.shape,
        )
    }

    private fun ValhallaManeuver.toManeuver(lastIndex: Int) = Maneuver(
        kind = valhallaKind(type),
        instruction = instruction,
        verbalAlert = verbalAlert,
        verbalPre = verbalPre,
        verbalPost = verbalPost,
        streetNames = streetNames.orEmpty(),
        lengthMeters = length * 1000,
        durationSeconds = time,
        beginShapeIndex = beginShapeIndex.coerceIn(0, lastIndex),
        endShapeIndex = endShapeIndex.coerceIn(0, lastIndex),
        roundaboutExit = roundaboutExitCount,
    )

    companion object {
        /** Valhalla maneuver type ids → our categories (see valhalla/odin docs). */
        fun valhallaKind(type: Int): ManeuverKind = when (type) {
            1, 2, 3 -> ManeuverKind.DEPART
            4, 5, 6 -> ManeuverKind.ARRIVE
            9 -> ManeuverKind.SLIGHT_RIGHT
            10 -> ManeuverKind.RIGHT
            11 -> ManeuverKind.SHARP_RIGHT
            12, 13 -> ManeuverKind.UTURN
            14 -> ManeuverKind.SHARP_LEFT
            15 -> ManeuverKind.LEFT
            16 -> ManeuverKind.SLIGHT_LEFT
            18, 20 -> ManeuverKind.RAMP_RIGHT
            19, 21 -> ManeuverKind.RAMP_LEFT
            23 -> ManeuverKind.KEEP_RIGHT
            24 -> ManeuverKind.KEEP_LEFT
            25, 37, 38 -> ManeuverKind.MERGE
            26 -> ManeuverKind.ROUNDABOUT_ENTER
            27 -> ManeuverKind.ROUNDABOUT_EXIT
            28, 29 -> ManeuverKind.FERRY
            else -> ManeuverKind.STRAIGHT // 7 becomes, 8 continue, 17 ramp straight, 22 stay straight
        }
    }
}
