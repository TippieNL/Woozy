package app.woozy.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceGuidanceTest {
    private val route = TestRoutes.lRoute
    private val tracker = RouteTracker(route)
    private val guidance = VoiceGuidance()

    private fun at(along: Double, speed: Double = 14.0) = guidance.next(route, tracker.maneuverStart, tracker.progressAt(along), speed)

    @Test
    fun `far then near announcement, each once`() {
        assertNull(at(1_000.0)) // 1 km before the turn: beyond the far threshold at 50 km/h
        assertEquals("In 400 metres, turn left onto B Street.", at(1_590.0))
        assertNull(at(1_600.0))
        assertEquals("Turn left onto B Street.", at(1_920.0))
        assertNull(at(1_950.0))
    }

    @Test
    fun `fast speed announces earlier`() {
        // 30 m/s (108 km/h) → far threshold 900 m
        assertNull(at(1_050.0, speed = 30.0)) // 950 m: just outside
        assertEquals("In 850 metres, turn left onto B Street.", at(1_150.0, speed = 30.0))
    }

    @Test
    fun `post instruction after depart when a long stretch follows`() {
        assertEquals("Continue for 2 kilometres.", at(50.0))
        assertNull(at(60.0))
    }

    @Test
    fun `spoken distances`() {
        assertEquals("300 metres", guidance.spokenDistance(310.0))
        assertEquals("1 kilometre", guidance.spokenDistance(1_020.0))
        assertEquals("1.5 kilometres", guidance.spokenDistance(1_500.0))
        assertEquals("2 kilometres", guidance.spokenDistance(2_010.0))
        assertEquals("12 kilometres", guidance.spokenDistance(12_300.0))
        val imperial = VoiceGuidance { true }
        assertEquals("500 feet", imperial.spokenDistance(150.0))
        assertEquals("3 miles", imperial.spokenDistance(4_900.0))
    }

    @Test
    fun `roundabout exits are silent and then-phrases suppress the follow-up alert`() {
        val base = TestRoutes.lRoute
        val roundabout = base.copy(
            maneuvers = listOf(
                base.maneuvers[0].copy(verbalPre = "Drive east. Then Enter the roundabout and take the 2nd exit."),
                base.maneuvers[1].copy(kind = app.woozy.domain.model.ManeuverKind.ROUNDABOUT_ENTER, beginShapeIndex = 1, verbalPre = "Enter the roundabout and take the 2nd exit."),
                base.maneuvers[1].copy(kind = app.woozy.domain.model.ManeuverKind.ROUNDABOUT_EXIT, beginShapeIndex = 2, verbalPre = "Exit the roundabout."),
                base.maneuvers[2],
            ),
        )
        val t = RouteTracker(roundabout)
        val g = VoiceGuidance()
        assertEquals("Drive east. Then Enter the roundabout and take the 2nd exit.", g.start(roundabout, t.maneuverStart, 14.0))
        // Roundabout 100 m ahead is already covered by "Then …"
        assertNull(g.next(roundabout, t.maneuverStart, t.progressAt(20.0), 14.0))
        // Exit maneuver 200 m in: never spoken
        assertNull(g.next(roundabout, t.maneuverStart, t.progressAt(150.0), 14.0))
        assertNull(g.next(roundabout, t.maneuverStart, t.progressAt(190.0), 14.0))
    }
}
