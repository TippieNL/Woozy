package app.woozy

import android.app.Application
import app.woozy.data.repository.ReportRepository
import app.woozy.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class WoozyApp : Application() {

    @Inject lateinit var reportRepository: ReportRepository

    @Inject @AppScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // No API key: tiles come from the style's own source URLs (OpenFreeMap by default).
        MapLibre.getInstance(this)
        appScope.launch { reportRepository.purgeExpired() }
    }
}
