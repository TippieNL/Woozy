package app.woozy.traffic

import app.woozy.domain.model.ReportType
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifetime and anti-duplicate rules for crowd reports. Defaults are local; a backend can push
 * overrides via [applyRemoteTtls] once one exists (expiry is a server decision long-term).
 */
@Singleton
class ReportPolicy @Inject constructor() {

    @Volatile
    private var ttlMinutes: Map<ReportType, Long> = DEFAULT_TTL_MINUTES

    fun ttlMillis(type: ReportType): Long = TimeUnit.MINUTES.toMillis(ttlMinutes.getValue(type))

    fun applyRemoteTtls(overrides: Map<ReportType, Long>) {
        ttlMinutes = DEFAULT_TTL_MINUTES + overrides.filterValues { it > 0 }
    }

    /** A same-type report by this device within this radius… */
    val duplicateRadiusMeters: Double = 200.0

    /** …and this time window is treated as a duplicate. */
    val duplicateWindowMillis: Long = TimeUnit.MINUTES.toMillis(10)

    /** Minimum gap between any two reports (accidental double taps). */
    val minIntervalMillis: Long = 4_000L

    companion object {
        val DEFAULT_TTL_MINUTES: Map<ReportType, Long> = mapOf(
            // A user-reported fixed camera needs backend verification before it is permanent.
            ReportType.SPEED_CAMERA to TimeUnit.DAYS.toMinutes(7),
            ReportType.MOBILE_SPEED_TRAP to 30L,
            ReportType.ACCIDENT to 60L,
            ReportType.TRAFFIC_JAM to 60L,
            ReportType.ROADWORKS to TimeUnit.HOURS.toMinutes(24),
            ReportType.ROAD_HAZARD to 120L,
            ReportType.STOPPED_VEHICLE to 45L,
            ReportType.OBJECT_ON_ROAD to 60L,
            ReportType.POLICE to 60L,
            ReportType.OTHER to 60L,
        )
    }
}
