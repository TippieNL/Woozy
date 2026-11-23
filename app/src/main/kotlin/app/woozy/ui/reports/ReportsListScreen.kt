package app.woozy.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.woozy.R
import app.woozy.data.repository.ReportRepository
import app.woozy.data.repository.SettingsRepository
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.ReportSource
import app.woozy.domain.model.SpeedUnit
import app.woozy.domain.model.TrafficReport
import app.woozy.location.LocationRepository
import app.woozy.ui.components.IconBadge
import app.woozy.ui.components.ScreenScaffold
import app.woozy.ui.theme.WoozyColors
import app.woozy.util.Geo
import app.woozy.util.formatDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ReportRow(val report: TrafficReport, val distanceMeters: Double?)

data class ReportsUiState(val rows: List<ReportRow> = emptyList(), val imperial: Boolean = false)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ReportRepository,
    locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val state: StateFlow<ReportsUiState> = combine(
        repository.activeReports,
        // Distances don't need 1 Hz updates in a list.
        locationRepository.fixes.sample(5_000L).onStart<LocationFix?> { emit(locationRepository.fixes.replayCache.lastOrNull()) },
        settingsRepository.settings,
    ) { reports, fix, settings ->
        val rows = reports
            .map { r -> ReportRow(r, fix?.let { Geo.distanceMeters(it.latitude, it.longitude, r.latitude, r.longitude) }) }
            .filter { it.distanceMeters == null || it.distanceMeters <= NEARBY_RADIUS_M }
            .sortedWith(compareBy<ReportRow> { it.distanceMeters ?: Double.MAX_VALUE }.thenByDescending { it.report.timestamp })
        ReportsUiState(rows, imperial = settings.speedUnit == SpeedUnit.MPH)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReportsUiState())

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }

    private companion object {
        const val NEARBY_RADIUS_M = 100_000.0
    }
}

@Composable
fun ReportsListScreen(viewModel: ReportsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.reports_title), onBack = null, modifier = modifier) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.reports_source_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                )
            }
            if (state.rows.isEmpty()) {
                item { EmptyState() }
            }
            items(state.rows, key = { it.report.id }) { row ->
                ReportRowItem(row, state.imperial, onRemove = { viewModel.remove(row.report.id) })
            }
        }
    }
}

@Composable
private fun ReportRowItem(row: ReportRow, imperial: Boolean, onRemove: () -> Unit) {
    val report = row.report
    val style = report.type.style
    val now = System.currentTimeMillis()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WoozyColors.PanelHigh)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(style.icon, Color(style.color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(style.label), style = MaterialTheme.typography.titleMedium)
            report.roadName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = WoozyColors.TextPrimary) }
            Text(
                listOf(
                    row.distanceMeters?.let { formatDistance(it, imperial) } ?: stringResource(R.string.reports_distance_unknown),
                    age(now - report.timestamp),
                    expiresIn(report.expiresAt - now),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (report.source == ReportSource.THIS_DEVICE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.reports_local_badge),
                    fontSize = 12.sp,
                    color = WoozyColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(WoozyColors.Accent.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        // Only your own (unshared) reports can be removed here; confirm/dismiss of others needs the backend.
        if (report.source == ReportSource.THIS_DEVICE) {
            IconButton(onClick = onRemove, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.ic_trash), stringResource(R.string.reports_remove), tint = WoozyColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_list), null, tint = WoozyColors.TextTertiary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.reports_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.reports_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun age(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis.coerceAtLeast(0))
    return when {
        minutes < 1 -> stringResource(R.string.reports_age_now)
        minutes < 60 -> stringResource(R.string.reports_age_min, minutes.toInt())
        else -> stringResource(R.string.reports_age_hour, (minutes / 60).toInt())
    }
}

@Composable
private fun expiresIn(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis.coerceAtLeast(0)).coerceAtLeast(1)
    return if (minutes < 120) stringResource(R.string.reports_expires_min, minutes.toInt())
    else stringResource(R.string.reports_expires_hour, (minutes / 60).toInt())
}
