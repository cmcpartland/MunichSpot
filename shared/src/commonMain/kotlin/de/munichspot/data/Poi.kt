package de.munichspot.data

enum class PoiType { RECYCLING, POST_BOX }

enum class Material { GLASS_BROWN, GLASS_GREEN, GLASS_WHITE, PACKAGING, CLOTHING }

data class Poi(
    val id: String,
    val type: PoiType,
    val name: String,
    val lat: Double,
    val lon: Double,
    val materials: List<Material> = emptyList(),
)
