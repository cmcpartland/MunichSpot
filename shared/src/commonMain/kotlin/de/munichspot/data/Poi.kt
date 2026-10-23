package de.munichspot.data

enum class PoiType { RECYCLING, CLOTHES, RECYCLING_CENTRE, POST_BOX, DRINKING_WATER, TOILET, PUBLIC_BOOKCASE }

enum class Material { GLASS_BROWN, GLASS_GREEN, GLASS_WHITE, PACKAGING, CLOTHING }

data class Poi(
    val id: String,
    val type: PoiType,
    val name: String,
    val lat: Double,
    val lon: Double,
    val materials: List<Material> = emptyList(),
    val note: LocalText? = null,
    val schedule: List<ScheduleRule>? = null,
    val toilet: ToiletInfo? = null,
    val recyclingCentre: RecyclingCentreInfo? = null,
    val postBox: PostBoxInfo? = null,
    val bookcase: BookcaseInfo? = null,
)

/** German source text with an optional machine translation. */
data class LocalText(val de: String, val en: String?)

data class PostBoxInfo(val address: String?, val collectionTimes: String?, val indoor: Boolean)

data class RecyclingCentreInfo(val address: String?)

data class BookcaseInfo(val phoneBox: Boolean, val indoor: Boolean)

enum class ToiletStatus { IMPAIRED, DEFECT }

data class ToiletInfo(
    val address: String?,
    val hours: LocalText?,
    val floor: String?,
    val price: Double?,
    val accessible: Boolean,
    val changing: Boolean,
    val euroKey: Boolean,
    val rooms: List<String>,
    val status: ToiletStatus?,
)
