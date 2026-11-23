package app.woozy.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrafficReportEntity::class, RecentDestinationEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)], // v2: favorites table
)
abstract class WoozyDatabase : RoomDatabase() {
    abstract fun trafficReportDao(): TrafficReportDao
    abstract fun recentDestinationDao(): RecentDestinationDao
    abstract fun favoriteDao(): FavoriteDao
}
