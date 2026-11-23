package app.woozy.data.providers

import app.woozy.domain.model.TrafficReport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backend contract for crowd reports (see README "Backend API"):
 *   GET  /api/reports/nearby   POST /api/reports
 *   POST /api/reports/{id}/confirm   POST /api/reports/{id}/dismiss
 */
interface ReportRemoteDataSource {
    /** True when a backend is configured. When false, reports stay on this device. */
    val isConfigured: Boolean

    suspend fun submit(report: TrafficReport): Result<Unit>

    suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int): Result<List<TrafficReport>>
}

/** Used until a backend exists. Never pretends to share anything. */
@Singleton
class NoBackendReportDataSource @Inject constructor() : ReportRemoteDataSource {
    override val isConfigured: Boolean = false

    override suspend fun submit(report: TrafficReport): Result<Unit> =
        Result.failure(IllegalStateException("No report backend configured"))

    override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int): Result<List<TrafficReport>> =
        Result.success(emptyList())
}
