package app.woozy.data

import app.woozy.data.api.ValhallaRouteResponse
import app.woozy.data.api.ValhallaTraceResponse
import app.woozy.data.providers.ValhallaRoutingProvider
import app.woozy.domain.model.ManeuverKind
import app.woozy.navigation.PolylineCodec
import app.woozy.navigation.RouteGeometry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real responses from the public Valhalla server (Haarlem Europaweg → Amsterdam, Nov 2026). */
class ValhallaFixtureTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun resource(name: String) = javaClass.classLoader!!.getResource("valhalla/$name")!!.readText()

    @Test
    fun `route response parses and geometry is consistent`() {
        val response = json.decodeFromString(ValhallaRouteResponse.serializer(), resource("route_haarlem_amsterdam.json"))
        val leg = response.trip.legs.single()
        val points = PolylineCodec.decode(leg.shape)
        val geometry = RouteGeometry(points)
        // Decoded polyline length agrees with Valhalla's own summary within 1 %.
        assertEquals(response.trip.summary.length * 1000, geometry.length, response.trip.summary.length * 10)
        assertTrue(leg.maneuvers.all { it.beginShapeIndex in points.indices && it.endShapeIndex in points.indices })
        assertEquals(ManeuverKind.DEPART, ValhallaRoutingProvider.valhallaKind(leg.maneuvers.first().type))
        assertEquals(ManeuverKind.ARRIVE, ValhallaRoutingProvider.valhallaKind(leg.maneuvers.last().type))
        assertTrue(leg.maneuvers.any { ValhallaRoutingProvider.valhallaKind(it.type) == ManeuverKind.ROUNDABOUT_ENTER })
        assertTrue(response.alternates.isNotEmpty())
    }

    @Test
    fun `speed limit spans cover the whole route`() {
        val route = json.decodeFromString(ValhallaRouteResponse.serializer(), resource("route_haarlem_amsterdam.json"))
        val last = PolylineCodec.decode(route.trip.legs.single().shape).lastIndex
        val edges = json.decodeFromString(ValhallaTraceResponse.serializer(), resource("trace_attributes_route.json")).edges
        assertEquals(0, edges.first().beginShapeIndex)
        assertEquals(last, edges.last().endShapeIndex)
        edges.zipWithNext().forEach { (a, b) -> assertEquals(a.endShapeIndex, b.beginShapeIndex) }
        assertTrue(edges.all { it.speedLimit in setOf(50, 80, 90, 100) })
    }
}
