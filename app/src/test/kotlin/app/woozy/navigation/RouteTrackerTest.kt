package app.woozy.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {
    private val route = TestRoutes.lRoute

    @Test
    fun `geometry length matches construction`() {
        assertEquals(3000.0, RouteGeometry(route.points).length, 15.0)
    }

    @Test
    fun `progress before the turn`() {
        val tracker = RouteTracker(route)
        val p = tracker.update(TestRoutes.eastOfStart(1_500.0, offsetNorthMeters = 6.0))!!
        assertEquals(1_500.0, p.distanceAlong, 10.0)
        assertEquals(6.0, p.distanceFromRoute, 1.0)
        assertEquals(1, p.nextManeuverIndex)
        assertEquals(500.0, p.distanceToNextManeuver, 10.0)
        assertEquals(1_500.0, p.remainingDistance, 15.0)
        // 25 % of the 200 s first leg + 100 s second leg
        assertEquals(150.0, p.remainingSeconds, 3.0)
        assertEquals("A Street", p.currentStreet)
        assertFalse(p.offRoute)
    }

    @Test
    fun `after the turn the next maneuver is arrival`() {
        val tracker = RouteTracker(route)
        val p = tracker.progressAt(2_500.0)
        assertEquals(2, p.nextManeuverIndex)
        assertEquals("B Street", p.currentStreet)
        assertEquals(500.0, p.distanceToNextManeuver, 15.0)
    }

    @Test
    fun `past the end there is no next maneuver`() {
        assertNull(RouteTracker(route).progressAt(3_100.0).nextManeuverIndex)
    }

    @Test
    fun `off route needs a streak of distant fixes`() {
        val tracker = RouteTracker(route)
        tracker.update(TestRoutes.eastOfStart(500.0))
        assertFalse(tracker.update(TestRoutes.eastOfStart(600.0, offsetNorthMeters = 150.0))!!.offRoute)
        assertFalse(tracker.update(TestRoutes.eastOfStart(650.0, offsetNorthMeters = 160.0))!!.offRoute)
        assertTrue(tracker.update(TestRoutes.eastOfStart(700.0, offsetNorthMeters = 170.0))!!.offRoute)
        // back on the road resets it
        assertFalse(tracker.update(TestRoutes.eastOfStart(800.0))!!.offRoute)
    }

    @Test
    fun `inaccurate fixes never trigger off route`() {
        val tracker = RouteTracker(route)
        repeat(5) {
            val f = TestRoutes.eastOfStart(500.0 + it * 20, offsetNorthMeters = 150.0)
            assertFalse(tracker.update(f.copy(accuracyMeters = 120f))!!.offRoute)
        }
    }

    @Test
    fun `remaining geometry starts at the snapped point`() {
        val geometry = RouteGeometry(route.points)
        val rest = geometry.remainingFrom(2_500.0)
        assertEquals(route.points.last(), rest.last())
        assertTrue(rest.size in 5..7)
    }
}
