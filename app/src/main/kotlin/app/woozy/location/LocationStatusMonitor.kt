package app.woozy.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class LocationPermission { GRANTED_PRECISE, GRANTED_APPROXIMATE, NOT_GRANTED }

/** Tracks location permission and whether device location (GPS switch) is on. */
@Singleton
class LocationStatusMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _permission = MutableStateFlow(readPermission())
    val permission: StateFlow<LocationPermission> = _permission.asStateFlow()

    /** Call after a permission result and on every resume (user may change it in Settings). */
    fun refreshPermission() {
        _permission.value = readPermission()
    }

    private fun readPermission(): LocationPermission = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermission.GRANTED_PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermission.GRANTED_APPROXIMATE
        else -> LocationPermission.NOT_GRANTED
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val locationManager = context.getSystemService(LocationManager::class.java)

    fun isLocationEnabled(): Boolean = locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

    /** Emits whenever the device location switch changes. */
    val locationEnabled: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(isLocationEnabled())
            }
        }
        trySend(isLocationEnabled())
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
