package app.woozy.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.woozy.R
import app.woozy.data.repository.SettingsRepository
import app.woozy.domain.model.MapOrientation
import app.woozy.domain.model.RouteOptions
import app.woozy.domain.model.RouteType
import app.woozy.domain.model.SPEEDING_TOLERANCE_CHOICES
import app.woozy.domain.model.SpeechRate
import app.woozy.domain.model.SpeedUnit
import app.woozy.domain.model.UserSettings
import app.woozy.services.VoiceAlertManager
import app.woozy.ui.components.ScreenScaffold
import app.woozy.ui.components.SectionHeader
import app.woozy.ui.theme.WoozyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val voice: VoiceAlertManager,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = repository.settings

    private fun update(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { repository.block() }
    }

    fun setAutoFollow(v: Boolean) = update { setAutoFollow(v) }
    fun setOrientation(v: MapOrientation) = update { setMapOrientation(v) }
    fun setPerspective3d(v: Boolean) = update { setPerspective3d(v) }
    fun setKeepScreenOn(v: Boolean) = update { setKeepScreenOn(v) }
    fun setUnit(v: SpeedUnit) = update { setSpeedUnit(v) }
    fun setVoiceAlerts(v: Boolean) = update { setVoiceAlertsEnabled(v) }
    fun setVoiceVolume(v: Float) = update { setVoiceVolume(v) }
    fun setSpeechRate(v: SpeechRate) = update { setSpeechRate(v) }
    fun setVoiceGuidance(v: Boolean) = update { setVoiceGuidance(v) }
    fun setSpeedingTolerance(v: Int) = update { setSpeedingTolerance(v) }
    fun setSpeedingVoice(v: Boolean) = update { setSpeedingVoiceAlert(v) }
    fun setRouteOptions(v: RouteOptions) = update { setRouteOptions(v) }

    fun testVoice() = voice.speak(context.getString(R.string.settings_test_voice_phrase), VoiceAlertManager.Priority.HIGH, force = true)
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_navigation))
            Group {
                SwitchRow(stringResource(R.string.settings_auto_follow), stringResource(R.string.settings_auto_follow_desc), s.autoFollow, viewModel::setAutoFollow)
                ChoiceRow(
                    title = stringResource(R.string.settings_orientation),
                    options = listOf(
                        MapOrientation.HEADING_UP to stringResource(R.string.settings_heading_up),
                        MapOrientation.NORTH_UP to stringResource(R.string.settings_north_up),
                    ),
                    selected = s.mapOrientation,
                    onSelect = viewModel::setOrientation,
                )
                SwitchRow(stringResource(R.string.settings_3d), stringResource(R.string.settings_3d_desc), s.perspective3d, viewModel::setPerspective3d)
                SwitchRow(stringResource(R.string.settings_keep_screen_on), stringResource(R.string.settings_keep_screen_on_desc), s.keepScreenOn, viewModel::setKeepScreenOn)
                SwitchRow(stringResource(R.string.settings_voice_guidance), null, s.voiceGuidance, viewModel::setVoiceGuidance)
                ChoiceRow(
                    title = stringResource(R.string.settings_route_type),
                    options = listOf(
                        RouteType.FASTEST to stringResource(R.string.route_fastest),
                        RouteType.SHORTEST to stringResource(R.string.route_shortest),
                    ),
                    selected = s.routeOptions.type,
                    onSelect = { viewModel.setRouteOptions(s.routeOptions.copy(type = it)) },
                )
                SwitchRow(stringResource(R.string.route_avoid_tolls), null, s.routeOptions.avoidTolls) { viewModel.setRouteOptions(s.routeOptions.copy(avoidTolls = it)) }
                SwitchRow(stringResource(R.string.route_avoid_highways), null, s.routeOptions.avoidHighways) { viewModel.setRouteOptions(s.routeOptions.copy(avoidHighways = it)) }
                SwitchRow(stringResource(R.string.route_avoid_ferries), null, s.routeOptions.avoidFerries) { viewModel.setRouteOptions(s.routeOptions.copy(avoidFerries = it)) }
            }

            SectionHeader(stringResource(R.string.settings_section_speed))
            Group {
                ChoiceRow(
                    title = stringResource(R.string.settings_units),
                    options = listOf(SpeedUnit.KMH to stringResource(R.string.settings_kmh), SpeedUnit.MPH to stringResource(R.string.settings_mph)),
                    selected = s.speedUnit,
                    onSelect = viewModel::setUnit,
                )
                val unitLabel = stringResource(if (s.speedUnit == SpeedUnit.KMH) R.string.settings_kmh else R.string.settings_mph)
                ChoiceRow(
                    title = stringResource(R.string.settings_speeding_threshold),
                    description = stringResource(R.string.settings_speeding_threshold_desc),
                    options = SPEEDING_TOLERANCE_CHOICES.map { it to if (it == 0) stringResource(R.string.settings_speeding_over) else "+$it" },
                    selected = s.speedingTolerance,
                    onSelect = viewModel::setSpeedingTolerance,
                    suffix = unitLabel,
                )
                SwitchRow(stringResource(R.string.settings_speeding_voice), null, s.speedingVoiceAlert, viewModel::setSpeedingVoice)
                Text(
                    stringResource(R.string.settings_speed_limit_note),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            SectionHeader(stringResource(R.string.settings_section_voice))
            Group {
                SwitchRow(stringResource(R.string.settings_voice_alerts), null, s.voiceAlertsEnabled, viewModel::setVoiceAlerts)
                VolumeRow(s.voiceVolume, viewModel::setVoiceVolume)
                ChoiceRow(
                    title = stringResource(R.string.settings_speech_rate),
                    options = listOf(
                        SpeechRate.SLOW to stringResource(R.string.settings_rate_slow),
                        SpeechRate.NORMAL to stringResource(R.string.settings_rate_normal),
                        SpeechRate.FAST to stringResource(R.string.settings_rate_fast),
                    ),
                    selected = s.speechRate,
                    onSelect = viewModel::setSpeechRate,
                )
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    OutlinedButton(onClick = viewModel::testVoice, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.settings_test_voice))
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WoozyColors.PanelHigh),
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = WoozyColors.TextPrimary)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = WoozyColors.Accent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    description: String? = null,
    suffix: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = WoozyColors.TextPrimary)
        description?.let { Text(if (suffix != null) "$it ($suffix)" else it, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = WoozyColors.Accent.copy(alpha = 0.25f),
                        activeContentColor = WoozyColors.TextPrimary,
                        inactiveContainerColor = WoozyColors.PanelHigh,
                        inactiveContentColor = WoozyColors.TextSecondary,
                    ),
                ) { Text(label) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(value: Float, onChange: (Float) -> Unit) {
    val sliderState = remember { SliderState(value = value, steps = 0, trackRange = 0.2f..1f) }
    LaunchedEffect(value) { sliderState.value = value }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_voice_volume), style = MaterialTheme.typography.bodyLarge, color = WoozyColors.TextPrimary)
        Slider(
            state = sliderState,
            onValueChange = { sliderState.value = it },
            onValueChangeFinished = { onChange(sliderState.value) },
            colors = SliderDefaults.colors(thumbColor = WoozyColors.Accent, activeTrackColor = WoozyColors.Accent),
        )
    }
}
