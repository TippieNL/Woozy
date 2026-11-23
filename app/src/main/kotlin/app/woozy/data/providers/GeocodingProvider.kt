package app.woozy.data.providers

import app.woozy.data.api.PhotonApi
import app.woozy.data.api.PhotonFeature
import app.woozy.domain.model.GeoPoint
import app.woozy.domain.model.Place
import javax.inject.Inject
import javax.inject.Singleton

/** Forward geocoding. Swap the binding in di/ProvidersModule to change provider. */
interface GeocodingProvider {
    suspend fun search(query: String, near: GeoPoint?): List<Place>
}

@Singleton
class PhotonGeocodingProvider @Inject constructor(
    private val api: PhotonApi,
) : GeocodingProvider {

    override suspend fun search(query: String, near: GeoPoint?): List<Place> =
        api.search(query = query, lat = near?.latitude, lon = near?.longitude, biasScale = near?.let { 0.4 })
            .features
            .mapNotNull { it.toPlace() }
            .distinctBy { it.id }

    private fun PhotonFeature.toPlace(): Place? {
        val (lon, lat) = geometry.coordinates.takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: return null
        val p = properties
        val streetLine = listOfNotNull(p.street, p.houseNumber).joinToString(" ").ifBlank { null }
        val name = p.name ?: streetLine ?: p.city ?: return null
        val locality = p.city ?: p.locality ?: p.district
        val address = listOfNotNull(
            streetLine.takeIf { it != name },
            listOfNotNull(p.postCode, locality.takeIf { it != name }).joinToString(" ").ifBlank { null },
            p.country,
        ).joinToString(", ").ifBlank { null }
        val id = if (p.osmId != null && p.osmType != null) "osm:${p.osmType}${p.osmId}" else "geo:%.6f,%.6f".format(lat, lon)
        return Place(id = id, name = name, address = address, latitude = lat, longitude = lon, category = p.osmValue)
    }
}
