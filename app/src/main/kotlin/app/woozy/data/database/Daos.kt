package app.woozy.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficReportDao {
    @Query("SELECT * FROM traffic_reports WHERE expiresAt > :now ORDER BY timestamp DESC")
    fun observeActive(now: Long): Flow<List<TrafficReportEntity>>

    @Query(
        """
        SELECT * FROM traffic_reports
        WHERE type = :type AND timestamp >= :since AND expiresAt > :now
          AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
        """,
    )
    suspend fun findRecentOfTypeInBox(
        type: String,
        since: Long,
        now: Long,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<TrafficReportEntity>

    @Query("SELECT MAX(timestamp) FROM traffic_reports WHERE source = :source")
    suspend fun latestTimestampFor(source: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: TrafficReportEntity)

    @Query("DELETE FROM traffic_reports WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM traffic_reports WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long): Int
}

@Dao
interface RecentDestinationDao {
    @Query("SELECT * FROM recent_destinations ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentDestinationEntity>>

    @Upsert
    suspend fun upsert(destination: RecentDestinationEntity)

    @Query("DELETE FROM recent_destinations WHERE placeId NOT IN (SELECT placeId FROM recent_destinations ORDER BY lastUsedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM recent_destinations")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE kind = :kind")
    suspend fun delete(kind: String)
}
