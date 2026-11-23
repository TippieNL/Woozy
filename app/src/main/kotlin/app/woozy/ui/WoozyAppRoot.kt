package app.woozy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.woozy.BuildConfig
import app.woozy.R
import app.woozy.ui.components.MainTab
import app.woozy.ui.components.WoozyBottomBar
import app.woozy.ui.map.MapScreen
import app.woozy.ui.map.MapViewModel
import app.woozy.ui.menu.InfoScreen
import app.woozy.ui.menu.MenuScreen
import app.woozy.ui.menu.MenuViewModel
import app.woozy.ui.reports.ReportsListScreen
import app.woozy.ui.search.SearchScreen
import app.woozy.ui.settings.SettingsScreen
import app.woozy.ui.theme.WoozyColors

/** Full-screen pages stacked over the tabs. */
private sealed interface Overlay {
    data class Search(val initialQuery: String) : Overlay
    data object Settings : Overlay
    data object About : Overlay
    data object Privacy : Overlay
}

/**
 * App shell. Deliberately not navigation-compose: the MapView must stay alive while other pages
 * are shown (re-creating a GL map on every tab switch costs a style reload and a visible flash),
 * so the map is always composed and List / Menu / overlays are drawn on top of it.
 */
@Composable
fun WoozyAppRoot() {
    var tab by rememberSaveable { mutableStateOf(MainTab.MAP) }
    val overlays = remember { mutableStateListOf<Overlay>() }
    val context = LocalContext.current
    val mapViewModel: MapViewModel = hiltViewModel()

    BackHandler(enabled = overlays.isNotEmpty()) { overlays.removeAt(overlays.lastIndex) }
    BackHandler(enabled = overlays.isEmpty() && tab != MainTab.MAP) { tab = MainTab.MAP }

    Box(Modifier.fillMaxSize().background(WoozyColors.Black)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MapScreen(
                    viewModel = mapViewModel,
                    onOpenSearch = { query -> overlays.add(Overlay.Search(query)) },
                )
                when (tab) {
                    MainTab.LIST -> ReportsListScreen(hiltViewModel())
                    MainTab.MENU -> {
                        val menuViewModel: MenuViewModel = hiltViewModel()
                        val cleared = stringResource(R.string.menu_recents_cleared)
                        MenuScreen(
                            onSettings = { overlays.add(Overlay.Settings) },
                            onAbout = { overlays.add(Overlay.About) },
                            onPrivacy = { overlays.add(Overlay.Privacy) },
                            onClearRecents = {
                                menuViewModel.clearRecents { Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show() }
                            },
                        )
                    }
                    MainTab.MAP -> Unit
                }
            }
            WoozyBottomBar(selected = tab, onSelect = { tab = it })
        }

        AnimatedContent(
            targetState = overlays.lastOrNull(),
            transitionSpec = {
                (slideInVertically { it / 8 } + fadeIn()) togetherWith (slideOutVertically { it / 8 } + fadeOut())
            },
            label = "overlay",
        ) { overlay ->
            when (overlay) {
                null -> Unit
                is Overlay.Search -> SearchScreen(
                    viewModel = hiltViewModel(),
                    initialQuery = overlay.initialQuery,
                    onClose = { overlays.remove(overlay) },
                    onPlaceSelected = {
                        overlays.clear()
                        tab = MainTab.MAP
                    },
                )
                Overlay.Settings -> SettingsScreen(hiltViewModel(), onBack = { overlays.remove(overlay) })
                Overlay.About -> InfoScreen(
                    title = stringResource(R.string.about_title),
                    body = stringResource(R.string.about_body, BuildConfig.VERSION_NAME),
                    onBack = { overlays.remove(overlay) },
                )
                Overlay.Privacy -> InfoScreen(
                    title = stringResource(R.string.privacy_title),
                    body = stringResource(R.string.privacy_body),
                    onBack = { overlays.remove(overlay) },
                )
            }
        }
    }
}
