package app.woozy.ui.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.woozy.BuildConfig
import app.woozy.R
import app.woozy.data.repository.DestinationRepository
import app.woozy.ui.components.ScreenScaffold
import app.woozy.ui.theme.WoozyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(private val destinations: DestinationRepository) : ViewModel() {
    fun clearRecents(onDone: () -> Unit) {
        viewModelScope.launch {
            destinations.clearRecents()
            onDone()
        }
    }
}

@Composable
fun MenuScreen(
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.menu_title), onBack = null, modifier = modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WoozyColors.PanelHigh)) {
                MenuRow(R.drawable.ic_settings, stringResource(R.string.menu_settings), onSettings)
                HorizontalDivider(color = WoozyColors.Divider, modifier = Modifier.padding(start = 60.dp))
                MenuRow(R.drawable.ic_history, stringResource(R.string.menu_clear_recents), onClearRecents, showChevron = false)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WoozyColors.PanelHigh)) {
                MenuRow(R.drawable.ic_privacy, stringResource(R.string.menu_privacy), onPrivacy)
                HorizontalDivider(color = WoozyColors.Divider, modifier = Modifier.padding(start = 60.dp))
                MenuRow(R.drawable.ic_info, stringResource(R.string.menu_about), onAbout)
            }
            Text(
                "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = WoozyColors.TextTertiary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun MenuRow(@DrawableRes icon: Int, title: String, onClick: () -> Unit, showChevron: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = WoozyColors.Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = WoozyColors.TextPrimary, modifier = Modifier.weight(1f))
        if (showChevron) Icon(painterResource(R.drawable.ic_chevron_right), null, tint = WoozyColors.TextTertiary)
    }
}

/** Plain text page (About, Privacy). */
@Composable
fun InfoScreen(title: String, body: String, onBack: () -> Unit) {
    ScreenScaffold(title = title, onBack = onBack) {
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = WoozyColors.TextSecondary,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }
}
