package de.munichspot.data

enum class PoiType { RECYCLING, POST_BOX, DRINKING_WATER, TOILET }

enum class Material { GLASS_BROWN, GLASS_GREEN, GLASS_WHITE, PACKAGING, CLOTHING }

data class Poi(
    val id: String,
    val type: PoiType,
    val name: String,
    val lat: Double,
    val lon: Double,
    val materials: List<Material> = emptyList(),
    val note: String? = null,
    val toilet: ToiletInfo? = null,
    val postBox: PostBoxInfo? = null,
)

data class PostBoxInfo(val address: String?, val collectionTimes: String?, val indoor: Boolean)

enum class ToiletStatus { IMPAIRED, DEFECT }

data class ToiletInfo(
    val address: String?,
    val hours: String?,
    val floor: String?,
    val price: Double?,
    val accessible: Boolean,
    val changing: Boolean,
    val euroKey: Boolean,
    val rooms: List<String>,
    val status: ToiletStatus?,
)
