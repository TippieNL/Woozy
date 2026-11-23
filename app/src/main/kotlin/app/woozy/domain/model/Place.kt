package app.woozy.domain.model

/** A geocoded destination (search result, recent or favourite). */
data class Place(
    val id: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
)
