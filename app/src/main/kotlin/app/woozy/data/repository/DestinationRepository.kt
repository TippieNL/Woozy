package app.woozy.data.repository

import app.woozy.data.database.FavoriteDao
import app.woozy.data.database.FavoriteEntity
import app.woozy.data.database.RecentDestinationDao
import app.woozy.data.database.RecentDestinationEntity
import app.woozy.data.providers.GeocodingProvider
import app.woozy.domain.model.FavoriteKind
import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Search, recent destinations and the currently selected destination. */
@Singleton
class DestinationRepository @Inject constructor(
    private val geocoder: GeocodingProvider,
    private val recentDao: RecentDestinationDao,
    private val favoriteDao: FavoriteDao,
) {
    private val _selected = MutableStateFlow<Place?>(null)
    val selected: StateFlow<Place?> = _selected.asStateFlow()

    val recents: Flow<List<Place>> = recentDao.observeRecent(limit = MAX_RECENTS).map { list ->
        list.map { Place(it.placeId, it.name, it.address, it.latitude, it.longitude, it.category) }
    }

    val favorites: Flow<Map<FavoriteKind, Place>> = favoriteDao.observeAll().map { list ->
        list.mapNotNull { e ->
            val kind = FavoriteKind.entries.firstOrNull { it.name == e.kind } ?: return@mapNotNull null
            kind to Place(e.placeId, e.name, e.address, e.latitude, e.longitude)
        }.toMap()
    }

    suspend fun setFavorite(kind: FavoriteKind, place: Place) = favoriteDao.upsert(
        FavoriteEntity(kind.name, place.id, place.name, place.address, place.latitude, place.longitude),
    )

    suspend fun favorite(kind: FavoriteKind): Place? = favorites.first()[kind]

    suspend fun search(query: String, near: GeoPoint?): List<Place> = geocoder.search(query, near)

    suspend fun select(place: Place) {
        _selected.value = place
        recentDao.upsert(
            RecentDestinationEntity(
                placeId = place.id,
                name = place.name,
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                category = place.category,
                lastUsedAt = System.currentTimeMillis(),
            ),
        )
        recentDao.trim(MAX_RECENTS)
    }

    fun clearSelection() {
        _selected.value = null
    }

    suspend fun clearRecents() = recentDao.clear()

    private companion object {
        const val MAX_RECENTS = 15
    }
}
