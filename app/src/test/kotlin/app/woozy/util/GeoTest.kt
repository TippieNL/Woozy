package app.woozy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun `amsterdam to utrecht is about 35 km`() {
        val d = Geo.distanceMeters(52.3791, 4.9003, 52.0894, 5.1100)
        assertEquals(35_400.0, d, 600.0)
    }

    @Test
    fun `bearing due east is 90`() {
        assertEquals(90.0, Geo.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.01)
    }

    @Test
    fun formatting() {
        assertEquals("350 m", formatDistance(354.0, imperial = false))
        assertEquals("1.2 km", formatDistance(1_234.0, imperial = false))
        assertEquals("12 km", formatDistance(12_345.0, imperial = false))
    }
}
