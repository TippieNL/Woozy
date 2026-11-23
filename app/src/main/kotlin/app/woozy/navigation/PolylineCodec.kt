package app.woozy.navigation

import app.woozy.domain.model.GeoPoint

/** Google encoded-polyline algorithm. Valhalla uses precision 6 (1e6). */
object PolylineCodec {
    fun decode(encoded: String, precision: Int = 6): List<GeoPoint> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<GeoPoint>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            result = 0L
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            out += GeoPoint(lat / factor, lon / factor)
        }
        return out
    }

    fun encode(points: List<GeoPoint>, precision: Int = 6): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = Math.round(p.latitude * factor)
            val lon = Math.round(p.longitude * factor)
            encodeValue(lat - prevLat, sb)
            encodeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }
}
