package app.woozy.domain.model

enum class ReportType {
    SPEED_CAMERA,
    MOBILE_SPEED_TRAP,
    ACCIDENT,
    TRAFFIC_JAM,
    ROADWORKS,
    ROAD_HAZARD,
    STOPPED_VEHICLE,
    OBJECT_ON_ROAD,
    POLICE,

    /** Shown to the user as "Dangerous situation". */
    OTHER,
}

/** Where a report came from. Development mock data must never be shown as real. */
enum class ReportSource { THIS_DEVICE, REMOTE, DEVELOPMENT_MOCK }

enum class SyncState { LOCAL_ONLY, PENDING, SYNCED }

data class TrafficReport(
    val id: String,
    val type: ReportType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val description: String?,
    val confidence: Int,
    val expiresAt: Long,
    val direction: Float?,
    val roadName: String?,
    val source: ReportSource,
    val syncState: SyncState,
)
