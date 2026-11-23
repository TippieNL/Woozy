package app.woozy

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.woozy.location.LocationStatusMonitor
import app.woozy.ui.WoozyAppRoot
import app.woozy.ui.theme.WoozyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var locationStatusMonitor: LocationStatusMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark: light status/navigation bar icons over the dark map.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            WoozyTheme {
                WoozyAppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the permission in system settings while we were away.
        locationStatusMonitor.refreshPermission()
    }
}
