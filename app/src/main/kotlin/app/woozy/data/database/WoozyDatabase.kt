package app.woozy.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrafficReportEntity::class, RecentDestinationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WoozyDatabase : RoomDatabase() {
    abstract fun trafficReportDao(): TrafficReportDao
    abstract fun recentDestinationDao(): RecentDestinationDao
}
