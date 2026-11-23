package app.woozy.data.repository

import app.woozy.data.database.TrafficReportDao
import app.woozy.data.database.TrafficReportEntity
import app.woozy.data.providers.ReportRemoteDataSource
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.ReportSource
import app.woozy.domain.model.ReportType
import app.woozy.domain.model.SyncState
import app.woozy.domain.model.TrafficReport
import app.woozy.traffic.ReportPolicy
import app.woozy.util.Geo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SubmitResult {
    data class Submitted(val report: TrafficReport, val shared: Boolean) : SubmitResult
    data object Duplicate : SubmitResult
    data object TooSoon : SubmitResult
    data object NoLocation : SubmitResult
}

@Singleton
class ReportRepository @Inject constructor(
    private val dao: TrafficReportDao,
    private val remote: ReportRemoteDataSource,
    private val policy: ReportPolicy,
) {
    private val submitLock = Mutex()

    /** Active (non-expired) reports; re-evaluated every 30 s so expired ones disappear on time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeReports: Flow<List<TrafficReport>> = ticker(30_000L)
        .flatMapLatest { now -> dao.observeActive(now) }
        .map { list -> list.mapNotNull { it.toDomain() } }
        .distinctUntilChanged()

    suspend fun submit(type: ReportType, fix: LocationFix?, roadName: String?, now: Long = System.currentTimeMillis()): SubmitResult =
        submitLock.withLock {
            if (fix == null) return SubmitResult.NoLocation
            val last = dao.latestTimestampFor(ReportSource.THIS_DEVICE.name)
            if (last != null && now - last < policy.minIntervalMillis) return SubmitResult.TooSoon

            val box = Geo.boundingBox(fix.latitude, fix.longitude, policy.duplicateRadiusMeters)
            val nearbySameType = dao.findRecentOfTypeInBox(
                type = type.name,
                since = now - policy.duplicateWindowMillis,
                now = now,
                minLat = box.minLat, maxLat = box.maxLat, minLon = box.minLon, maxLon = box.maxLon,
            ).any { Geo.distanceMeters(it.latitude, it.longitude, fix.latitude, fix.longitude) <= policy.duplicateRadiusMeters }
            if (nearbySameType) return SubmitResult.Duplicate

            val report = TrafficReport(
                id = UUID.randomUUID().toString(),
                type = type,
                latitude = fix.latitude,
                longitude = fix.longitude,
                timestamp = now,
                description = null,
                confidence = 1,
                expiresAt = now + policy.ttlMillis(type),
                direction = fix.bearingDegrees,
                roadName = roadName,
                source = ReportSource.THIS_DEVICE,
                syncState = if (remote.isConfigured) SyncState.PENDING else SyncState.LOCAL_ONLY,
            )
            dao.insert(report.toEntity())

            val shared = remote.isConfigured && remote.submit(report).isSuccess
            if (shared) dao.insert(report.copy(syncState = SyncState.SYNCED).toEntity())
            SubmitResult.Submitted(report, shared)
        }

    suspend fun remove(id: String) = dao.delete(id)

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) = dao.deleteExpired(now)

    private fun ticker(periodMillis: Long): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(periodMillis)
        }
    }
}

private fun TrafficReport.toEntity() = TrafficReportEntity(
    id = id,
    type = type.name,
    latitude = latitude,
    longitude = longitude,
    timestamp = timestamp,
    description = description,
    confidence = confidence,
    expiresAt = expiresAt,
    direction = direction,
    roadName = roadName,
    source = source.name,
    syncState = syncState.name,
)

private fun TrafficReportEntity.toDomain(): TrafficReport? {
    val type = ReportType.entries.firstOrNull { it.name == type } ?: return null
    return TrafficReport(
        id = id,
        type = type,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        description = description,
        confidence = confidence,
        expiresAt = expiresAt,
        direction = direction,
        roadName = roadName,
        source = ReportSource.entries.firstOrNull { it.name == source } ?: ReportSource.THIS_DEVICE,
        syncState = SyncState.entries.firstOrNull { it.name == syncState } ?: SyncState.LOCAL_ONLY,
    )
}
