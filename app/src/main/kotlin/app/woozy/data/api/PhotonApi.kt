package app.woozy.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Photon geocoder (https://photon.komoot.io, OpenStreetMap based). Designed for search-as-you-type,
 * unlike Nominatim whose usage policy forbids autocomplete. The public instance is fair-use only;
 * point GEOCODER_BASE_URL at a self-hosted instance for production.
 */
interface PhotonApi {
    @GET("api")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 12,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        /** Ranking bias towards lat/lon; higher = stronger. */
        @Query("location_bias_scale") biasScale: Double? = null,
    ): PhotonResponse
}

@Serializable
data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
data class PhotonFeature(
    val geometry: PhotonGeometry,
    val properties: PhotonProperties,
)

@Serializable
data class PhotonGeometry(val coordinates: List<Double>)

@Serializable
data class PhotonProperties(
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_key") val osmKey: String? = null,
    @SerialName("osm_value") val osmValue: String? = null,
    val type: String? = null,
    val name: String? = null,
    val street: String? = null,
    @SerialName("housenumber") val houseNumber: String? = null,
    @SerialName("postcode") val postCode: String? = null,
    val city: String? = null,
    val district: String? = null,
    val locality: String? = null,
    val state: String? = null,
    val country: String? = null,
)
