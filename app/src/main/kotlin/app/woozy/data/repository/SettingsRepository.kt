package app.woozy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.woozy.di.AppScope
import app.woozy.domain.model.MapOrientation
import app.woozy.domain.model.RouteOptions
import app.woozy.domain.model.SpeechRate
import app.woozy.domain.model.SpeedUnit
import app.woozy.domain.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @AppScope scope: CoroutineScope,
) {
    private object Keys {
        val unit = stringPreferencesKey("speed_unit")
        val orientation = stringPreferencesKey("map_orientation")
        val perspective3d = booleanPreferencesKey("perspective_3d")
        val autoFollow = booleanPreferencesKey("auto_follow")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val voiceAlerts = booleanPreferencesKey("voice_alerts")
        val voiceVolume = floatPreferencesKey("voice_volume")
        val speechRate = stringPreferencesKey("speech_rate")
        val voiceGuidance = booleanPreferencesKey("voice_guidance")
        val speedingTolerance = intPreferencesKey("speeding_tolerance")
        val speedingVoice = booleanPreferencesKey("speeding_voice_alert")
        val routeType = stringPreferencesKey("route_type")
        val avoidTolls = booleanPreferencesKey("avoid_tolls")
        val avoidHighways = booleanPreferencesKey("avoid_highways")
        val avoidFerries = booleanPreferencesKey("avoid_ferries")
    }

    private val defaults = UserSettings()

    val settings: StateFlow<UserSettings> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            UserSettings(
                speedUnit = p[Keys.unit].toEnum(defaults.speedUnit),
                mapOrientation = p[Keys.orientation].toEnum(defaults.mapOrientation),
                perspective3d = p[Keys.perspective3d] ?: defaults.perspective3d,
                autoFollow = p[Keys.autoFollow] ?: defaults.autoFollow,
                keepScreenOn = p[Keys.keepScreenOn] ?: defaults.keepScreenOn,
                voiceAlertsEnabled = p[Keys.voiceAlerts] ?: defaults.voiceAlertsEnabled,
                voiceVolume = p[Keys.voiceVolume] ?: defaults.voiceVolume,
                speechRate = p[Keys.speechRate].toEnum(defaults.speechRate),
                voiceGuidance = p[Keys.voiceGuidance] ?: defaults.voiceGuidance,
                speedingTolerance = p[Keys.speedingTolerance] ?: defaults.speedingTolerance,
                speedingVoiceAlert = p[Keys.speedingVoice] ?: defaults.speedingVoiceAlert,
                routeOptions = RouteOptions(
                    type = p[Keys.routeType].toEnum(defaults.routeOptions.type),
                    avoidTolls = p[Keys.avoidTolls] ?: defaults.routeOptions.avoidTolls,
                    avoidHighways = p[Keys.avoidHighways] ?: defaults.routeOptions.avoidHighways,
                    avoidFerries = p[Keys.avoidFerries] ?: defaults.routeOptions.avoidFerries,
                ),
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, defaults)

    suspend fun setSpeedUnit(value: SpeedUnit) = dataStore.edit { it[Keys.unit] = value.name }
    suspend fun setMapOrientation(value: MapOrientation) = dataStore.edit { it[Keys.orientation] = value.name }
    suspend fun setPerspective3d(value: Boolean) = dataStore.edit { it[Keys.perspective3d] = value }
    suspend fun setAutoFollow(value: Boolean) = dataStore.edit { it[Keys.autoFollow] = value }
    suspend fun setKeepScreenOn(value: Boolean) = dataStore.edit { it[Keys.keepScreenOn] = value }
    suspend fun setVoiceAlertsEnabled(value: Boolean) = dataStore.edit { it[Keys.voiceAlerts] = value }
    suspend fun setVoiceVolume(value: Float) = dataStore.edit { it[Keys.voiceVolume] = value.coerceIn(0f, 1f) }
    suspend fun setSpeechRate(value: SpeechRate) = dataStore.edit { it[Keys.speechRate] = value.name }

    suspend fun setVoiceGuidance(value: Boolean) = dataStore.edit { it[Keys.voiceGuidance] = value }
    suspend fun setSpeedingTolerance(value: Int) = dataStore.edit { it[Keys.speedingTolerance] = value.coerceIn(0, 30) }
    suspend fun setSpeedingVoiceAlert(value: Boolean) = dataStore.edit { it[Keys.speedingVoice] = value }
    suspend fun setRouteOptions(value: RouteOptions) = dataStore.edit {
        it[Keys.routeType] = value.type.name
        it[Keys.avoidTolls] = value.avoidTolls
        it[Keys.avoidHighways] = value.avoidHighways
        it[Keys.avoidFerries] = value.avoidFerries
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
