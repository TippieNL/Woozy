package app.woozy.location

import app.woozy.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedCalculatorTest {

    private fun fix(
        seconds: Double,
        lat: Double = 52.0,
        lon: Double = 5.0,
        speed: Float? = null,
        speedAcc: Float? = null,
        acc: Float? = 5f,
        cached: Boolean = false,
    ) = LocationFix(
        latitude = lat, longitude = lon, accuracyMeters = acc, speedMps = speed, speedAccuracyMps = speedAcc,
        bearingDegrees = null, bearingAccuracyDegrees = null, altitudeMeters = null, timeMillis = 0L,
        elapsedRealtimeNanos = (seconds * 1e9).toLong(), provider = "gps", isCached = cached,
    )

    @Test
    fun `uses reported doppler speed`() {
        val calc = SpeedCalculator(alpha = 1.0)
        assertEquals(27.8, calc.update(fix(1.0, speed = 27.8f))!!, 0.01)
    }

    @Test
    fun `smooths jumps with ema`() {
        val calc = SpeedCalculator(alpha = 0.5)
        calc.update(fix(1.0, speed = 20f))
        assertEquals(25.0, calc.update(fix(2.0, speed = 30f))!!, 0.01)
    }

    @Test
    fun `snaps low speeds to zero`() {
        val calc = SpeedCalculator(alpha = 1.0)
        assertEquals(0.0, calc.update(fix(1.0, speed = 0.4f))!!, 0.0)
    }

    @Test
    fun `ignores implausible spikes`() {
        val calc = SpeedCalculator(alpha = 1.0)
        calc.update(fix(1.0, speed = 30f))
        assertEquals(30.0, calc.update(fix(2.0, speed = 400f))!!, 0.01)
    }

    @Test
    fun `cached fixes never produce a speed`() {
        val calc = SpeedCalculator()
        assertNull(calc.update(fix(1.0, speed = 30f, cached = true)))
    }

    @Test
    fun `derives speed from positions when no doppler speed`() {
        val calc = SpeedCalculator(alpha = 1.0)
        calc.update(fix(1.0, lat = 52.0))
        // ~0.0002 deg latitude ≈ 22.2 m in 1 s
        val v = calc.update(fix(2.0, lat = 52.0002))
        assertNotNull(v)
        assertEquals(22.2, v!!, 0.5)
    }

    @Test
    fun `movement within accuracy counts as stationary`() {
        val calc = SpeedCalculator(alpha = 1.0)
        calc.update(fix(1.0, lat = 52.0, acc = 10f))
        assertEquals(0.0, calc.update(fix(2.0, lat = 52.00003, acc = 10f))!!, 0.0)
    }

    @Test
    fun `inaccurate doppler falls back to positions`() {
        val calc = SpeedCalculator(alpha = 1.0)
        calc.update(fix(1.0, lat = 52.0))
        val v = calc.update(fix(2.0, lat = 52.0002, speed = 80f, speedAcc = 20f))
        assertTrue(v!! < 30)
    }

    @Test
    fun `speed becomes unknown after a long gap without data`() {
        val calc = SpeedCalculator(alpha = 1.0)
        calc.update(fix(1.0, speed = 20f))
        // no speed, poor accuracy -> no usable sample; after >5 s the value expires
        assertNull(calc.update(fix(8.0, acc = 100f)))
    }
}
