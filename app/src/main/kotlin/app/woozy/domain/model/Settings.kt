package app.woozy.domain.model

enum class SpeedUnit(val factorFromMps: Double) {
    KMH(3.6),
    MPH(2.2369362920544);

    fun fromMps(mps: Double): Double = mps * factorFromMps
}

enum class MapOrientation { HEADING_UP, NORTH_UP }

enum class SpeechRate(val ttsRate: Float) { SLOW(0.85f), NORMAL(1.0f), FAST(1.2f) }

data class UserSettings(
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val mapOrientation: MapOrientation = MapOrientation.HEADING_UP,
    val perspective3d: Boolean = true,
    val autoFollow: Boolean = true,
    val keepScreenOn: Boolean = true,
    val voiceAlertsEnabled: Boolean = true,
    /** 0.0 – 1.0, relative to the navigation-guidance stream volume. */
    val voiceVolume: Float = 1.0f,
    val speechRate: SpeechRate = SpeechRate.NORMAL,
)
