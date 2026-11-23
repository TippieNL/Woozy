package app.woozy.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import app.woozy.R
import app.woozy.location.LocationPermission
import app.woozy.ui.theme.WoozyColors
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/**
 * Explains and requests location access, and helps turn device location on.
 * Renders nothing when everything is in order.
 */
@Composable
fun LocationAccessCard(
    permission: LocationPermission,
    locationEnabled: Boolean,
    onPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        asked = true
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val activity = context.findActivity()
        // No dialog was shown and no rationale is allowed → the user chose "don't ask again".
        permanentlyDenied = !fine && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        onPermissionResult()
    }
    val resolutionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    val request = {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    when {
        permission == LocationPermission.NOT_GRANTED -> AccessCard(
            title = stringResource(R.string.perm_title),
            body = stringResource(
                when {
                    permanentlyDenied -> R.string.perm_permanent_body
                    asked -> R.string.perm_denied_body
                    else -> R.string.perm_body
                },
            ),
            action = stringResource(if (permanentlyDenied) R.string.perm_open_settings else R.string.perm_allow),
            onAction = { if (permanentlyDenied) context.openAppSettings() else request() },
            modifier = modifier,
        )

        permission == LocationPermission.GRANTED_APPROXIMATE -> AccessCard(
            title = stringResource(R.string.perm_precise_title),
            body = stringResource(R.string.perm_precise_body),
            action = stringResource(if (permanentlyDenied) R.string.perm_open_settings else R.string.perm_allow),
            onAction = { if (permanentlyDenied) context.openAppSettings() else request() },
            modifier = modifier,
        )

        !locationEnabled -> AccessCard(
            title = stringResource(R.string.location_off_title),
            body = stringResource(R.string.location_off_body),
            action = stringResource(R.string.location_off_action),
            onAction = {
                requestLocationOn(context) { sender -> resolutionLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            },
            modifier = modifier,
        )
    }
}

/** Tries the one-tap Play services dialog first, falls back to the system location settings. */
private fun requestLocationOn(context: Context, launch: (android.content.IntentSender) -> Unit) {
    val activity = context.findActivity()
    if (activity == null) {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        return
    }
    val request = LocationSettingsRequest.Builder()
        .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L).build())
        .setAlwaysShow(true)
        .build()
    runCatching {
        LocationServices.getSettingsClient(activity).checkLocationSettings(request)
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) launch(e.resolution.intentSender)
                else activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
    }.onFailure {
        activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}

@Composable
private fun AccessCard(title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(WoozyColors.PanelHigh)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_gps), null, tint = WoozyColors.Accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = WoozyColors.TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = WoozyColors.Accent),
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(action, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
