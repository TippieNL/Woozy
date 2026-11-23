package app.woozy.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.woozy.R
import app.woozy.data.repository.DestinationRepository
import app.woozy.data.repository.SettingsRepository
import app.woozy.domain.model.FavoriteKind
import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.Place
import app.woozy.domain.model.SpeedUnit
import app.woozy.domain.model.toGeoPoint
import app.woozy.location.LocationRepository
import app.woozy.ui.components.rememberSpeechInput
import app.woozy.ui.theme.WoozyColors
import app.woozy.util.Geo
import app.woozy.util.formatDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Loading : SearchResults
    data class Loaded(val places: List<Place>) : SearchResults
    data object Error : SearchResults
}

data class SearchUiState(
    val results: SearchResults = SearchResults.Idle,
    val favorites: Map<FavoriteKind, Place> = emptyMap(),
    val recents: List<Place> = emptyList(),
    val origin: GeoPoint? = null,
    val imperial: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val destinations: DestinationRepository,
    locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val origin: GeoPoint? = locationRepository.fixes.replayCache.lastOrNull()?.toGeoPoint()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val results = query
        .map { it.trim() }
        .debounce(350L)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow {
                if (q.length < 2) {
                    emit(SearchResults.Idle)
                    return@flow
                }
                emit(SearchResults.Loading)
                try {
                    emit(SearchResults.Loaded(destinations.search(q, origin)))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emit(SearchResults.Error)
                }
            }
        }

    val state: StateFlow<SearchUiState> = combine(
        results,
        destinations.recents,
        destinations.favorites,
        settingsRepository.settings,
    ) { r, recents, favorites, settings ->
        SearchUiState(r, favorites, recents, origin, settings.speedUnit == SpeedUnit.MPH)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SearchUiState(origin = origin))

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun select(place: Place, onDone: () -> Unit) {
        viewModelScope.launch {
            destinations.select(place)
            onDone()
        }
    }

    fun clearRecents() {
        viewModelScope.launch { destinations.clearRecents() }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel, initialQuery: String, onClose: () -> Unit, onPlaceSelected: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val textState = rememberTextFieldState(initialQuery)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var voiceUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(textState) {
        focus.requestFocus()
        snapshotFlow { textState.text.toString() }.collect(viewModel::onQueryChange)
    }
    val dictate = rememberSpeechInput(
        prompt = stringResource(R.string.search_hint),
        onResult = { textState.setTextAndPlaceCursorAtEnd(it) },
        onUnavailable = { voiceUnavailable = true; focus.requestFocus() },
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(WoozyColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), tint = WoozyColors.TextPrimary)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(WoozyColors.Field)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (textState.text.isEmpty()) {
                        Text(stringResource(R.string.search_hint), color = WoozyColors.TextTertiary, fontSize = 17.sp, maxLines = 1)
                    }
                    BasicTextField(
                        state = textState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TextStyle(color = WoozyColors.TextPrimary, fontSize = 17.sp),
                        cursorBrush = SolidColor(WoozyColors.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { keyboard?.hide() },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (textState.text.isNotEmpty()) {
                    IconButton(onClick = { textState.clearText() }, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = WoozyColors.TextSecondary)
                    }
                } else {
                    IconButton(onClick = dictate, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.dictate), tint = WoozyColors.TextSecondary)
                    }
                }
            }
        }
        if (voiceUnavailable) {
            Text(
                stringResource(R.string.voice_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = WoozyColors.Warning,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        val select: (Place) -> Unit = { place -> keyboard?.hide(); viewModel.select(place, onPlaceSelected) }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (val results = state.results) {
                SearchResults.Idle -> {
                    FavoriteKind.entries.forEach { kind ->
                        state.favorites[kind]?.let { place ->
                            item(key = "fav-" + kind.name) {
                                PlaceRow(
                                    place.copy(name = stringResource(if (kind == FavoriteKind.HOME) R.string.favorite_home else R.string.favorite_work), address = place.name),
                                    R.drawable.ic_pin,
                                    state.origin,
                                    state.imperial,
                                ) { select(place) }
                            }
                        }
                    }
                    if (state.recents.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.labelLarge, color = WoozyColors.Accent, modifier = Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearRecents) { Text(stringResource(R.string.search_clear_recent)) }
                            }
                        }
                        items(state.recents, key = { "recent-" + it.id }) { place ->
                            PlaceRow(place, R.drawable.ic_history, state.origin, state.imperial) { select(place) }
                        }
                    }
                }
                SearchResults.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WoozyColors.Accent)
                    }
                }
                SearchResults.Error -> item { CenterMessage(stringResource(R.string.search_error)) }
                is SearchResults.Loaded -> {
                    if (results.places.isEmpty()) item { CenterMessage(stringResource(R.string.search_no_results)) }
                    items(results.places, key = { it.id }) { place ->
                        PlaceRow(place, R.drawable.ic_pin, state.origin, state.imperial) { select(place) }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.search_attribution),
            fontSize = 11.sp,
            color = WoozyColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PlaceRow(place: Place, icon: Int, origin: GeoPoint?, imperial: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(WoozyColors.PanelHigh), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = WoozyColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            place.address?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        origin?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                formatDistance(Geo.distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude), imperial),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = WoozyColors.TextSecondary)
    }
}
