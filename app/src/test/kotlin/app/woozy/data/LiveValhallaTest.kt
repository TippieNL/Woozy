package app.woozy.data

import app.woozy.data.api.ValhallaApi
import app.woozy.data.providers.ValhallaRoutingProvider
import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.RouteOptions
import app.woozy.domain.model.RouteType
import app.woozy.navigation.RouteTracker
import app.woozy.navigation.VoiceGuidance
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Hits the real routing server. Skipped by default; run with
 *   WOOZY_LIVE_TESTS=1 ./gradlew :app:testDebugUnitTest --tests '*LiveValhallaTest*'
 */
class LiveValhallaTest {
    private lateinit var provider: ValhallaRoutingProvider
    private val haarlem = GeoPoint(52.3549, 4.6440)
    private val amsterdam = GeoPoint(52.3791, 4.9003)

    @Before
    fun setUp() {
        assumeTrue(System.getenv("WOOZY_LIVE_TESTS") == "1")
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val client = OkHttpClient.Builder()
            .addInterceptor { it.proceed(it.request().newBuilder().header("User-Agent", "Woozy-tests (Android navigation app)").build()) }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(System.getenv("ROUTING_BASE_URL") ?: "https://valhalla1.openstreetmap.de/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ValhallaApi::class.java)
        provider = ValhallaRoutingProvider(api, json)
    }

    @Test
    fun `route, speed limits and a simulated drive`() = runBlocking {
        val routes = provider.route(haarlem, amsterdam, RouteOptions())
        assertTrue(routes.isNotEmpty())
        val route = routes.first()
        println("routes: " + routes.joinToString { "%.1f km / %.0f min".format(it.lengthMeters / 1000, it.durationSeconds / 60) })

        val spans = provider.speedLimitsAlong(route)
        assertEquals(route.points.lastIndex, spans.last().endShapeIndex)
        println("speed limits along route: " + spans.mapNotNull { it.kmh }.distinct())

        // Drive the route at 50 km/h, one fix per second, and collect what would be spoken.
        val tracker = RouteTracker(route)
        val guidance = VoiceGuidance()
        val spoken = mutableListOf<String>()
        guidance.start(route, tracker.maneuverStart)?.let { spoken += "[start] $it" }
        var along = 0.0
        while (along < tracker.geometry.length) {
            val (_, p) = tracker.geometry.pointAt(along)
            val fix = app.woozy.navigation.TestRoutes.fix(p.latitude, p.longitude)
            val progress = tracker.update(fix)!!
            check(!progress.offRoute) { "off route at $along" }
            guidance.next(route, tracker.maneuverStart, progress, 14.0)?.let { spoken += "[%5.0f m] %s".format(along, it) }
            along += 14.0
        }
        println(spoken.take(14).joinToString("\n"))
        println("... last:\n" + spoken.takeLast(4).joinToString("\n") + "\n total spoken=${spoken.size}, maneuvers=${route.maneuvers.size}, last kind=${route.maneuvers.last().kind}, last pre=${route.maneuvers.last().verbalPre}")
        assertTrue(spoken.none { it.contains("Exit the roundabout") })
        assertTrue(spoken.last().endsWith(route.maneuvers.last().verbalPre!!))
    }

    @Test
    fun `shortest and avoid-highway options change the route`() = runBlocking {
        val fastest = provider.route(haarlem, amsterdam, RouteOptions(), alternatives = 0).first()
        val noHighway = provider.route(haarlem, amsterdam, RouteOptions(avoidHighways = true), alternatives = 0).first()
        val shortest = provider.route(haarlem, amsterdam, RouteOptions(type = RouteType.SHORTEST), alternatives = 0).first()
        println("fastest %.1f km, no-highway %.1f km (highway=%s), shortest %.1f km".format(
            fastest.lengthMeters / 1000, noHighway.lengthMeters / 1000, noHighway.hasHighway, shortest.lengthMeters / 1000))
        assertTrue(shortest.lengthMeters <= fastest.lengthMeters)
        assertTrue(noHighway.durationSeconds >= fastest.durationSeconds)
    }

    @Test
    fun `free-drive map matching returns the road limit`() = runBlocking {
        // Three points eastbound on the A10 west ring (80 km/h) near Amsterdam.
        val route = provider.route(GeoPoint(52.3600, 4.8340), GeoPoint(52.3900, 4.8380), RouteOptions(), alternatives = 0).first()
        val trail = route.points.take(8)
        val kmh = provider.speedLimitAt(trail)
        println("free-drive limit: $kmh")
        assertNotNull(kmh)
    }
}
