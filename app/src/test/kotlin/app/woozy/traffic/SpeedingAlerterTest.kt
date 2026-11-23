package app.woozy.traffic

import app.woozy.ui.map.MapViewModel
import app.woozy.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedingAlerterTest {
    @Test
    fun `waits for grace period then respects cooldown`() {
        val a = SpeedingAlerter(graceMillis = 3_000, cooldownMillis = 60_000)
        assertFalse(a.update(true, 50, 0))
        assertFalse(a.update(true, 50, 2_000))
        assertTrue(a.update(true, 50, 3_000))
        assertFalse(a.update(true, 50, 30_000))
        assertTrue(a.update(true, 50, 63_000))
    }

    @Test
    fun `brief overshoot is ignored and resets`() {
        val a = SpeedingAlerter(graceMillis = 3_000)
        assertFalse(a.update(true, 50, 0))
        assertFalse(a.update(false, 50, 1_000))
        assertFalse(a.update(true, 50, 3_500))
    }

    @Test
    fun `new limit warns again despite cooldown`() {
        val a = SpeedingAlerter(graceMillis = 0, cooldownMillis = 60_000)
        assertTrue(a.update(true, 100, 0))
        assertTrue(a.update(true, 80, 10_000))
    }

    @Test
    fun `speeding threshold`() {
        assertFalse(MapViewModel.isSpeeding(54, 50, 5))
        assertTrue(MapViewModel.isSpeeding(55, 50, 5))
        assertFalse(MapViewModel.isSpeeding(50, 50, 0))
        assertTrue(MapViewModel.isSpeeding(51, 50, 0))
        assertFalse(MapViewModel.isSpeeding(null, 50, 0))
        assertFalse(MapViewModel.isSpeeding(80, null, 0))
    }

    @Test
    fun `limit display in mph rounds to 5`() {
        assertEquals(100, MapViewModel.displayLimit(100, SpeedUnit.KMH))
        assertEquals(30, MapViewModel.displayLimit(50, SpeedUnit.MPH))
        assertEquals(60, MapViewModel.displayLimit(100, SpeedUnit.MPH))
        assertEquals(70, MapViewModel.displayLimit(110, SpeedUnit.MPH))
    }
}
