package app.woozy.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traffic_reports",
    indices = [Index("expiresAt"), Index("latitude", "longitude")],
)
data class TrafficReportEntity(
    @PrimaryKey val id: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val description: String?,
    val confidence: Int,
    val expiresAt: Long,
    val direction: Float?,
    val roadName: String?,
    val source: String,
    val syncState: String,
)

@Entity(tableName = "recent_destinations")
data class RecentDestinationEntity(
    @PrimaryKey val placeId: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String?,
    val lastUsedAt: Long,
)
