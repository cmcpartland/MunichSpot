package de.munichspot.data

enum class PoiType { RECYCLING, POST_BOX }

data class Poi(val id: String, val type: PoiType, val name: String, val lat: Double, val lon: Double)
