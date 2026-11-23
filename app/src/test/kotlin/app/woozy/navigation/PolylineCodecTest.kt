package app.woozy.navigation

import app.woozy.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineCodecTest {
    @Test
    fun `round trip precision 6`() {
        val points = listOf(GeoPoint(52.354951, 4.644061), GeoPoint(52.355012, 4.645001), GeoPoint(52.379100, 4.900300))
        val decoded = PolylineCodec.decode(PolylineCodec.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-6)
            assertEquals(a.longitude, b.longitude, 1e-6)
        }
    }

    @Test
    fun `decodes google reference polyline at precision 5`() {
        // Example from Google's polyline algorithm documentation.
        val decoded = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].latitude, 1e-5)
        assertEquals(-120.2, decoded[0].longitude, 1e-5)
        assertEquals(43.252, decoded[2].latitude, 1e-5)
        assertEquals(-126.453, decoded[2].longitude, 1e-5)
    }
}
