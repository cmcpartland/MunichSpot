package de.munichspot.map

import de.munichspot.data.Material
import de.munichspot.data.Poi
import de.munichspot.data.PoiType

internal enum class RecyclingFilter(val materials: Set<Material>) {
    GLASS(setOf(Material.GLASS_BROWN, Material.GLASS_GREEN, Material.GLASS_WHITE)),
    PACKAGING(setOf(Material.PACKAGING)),
    CLOTHING(setOf(Material.CLOTHING)),
}

internal data class PoiFilters(
    val recyclingMaterials: Set<RecyclingFilter> = RecyclingFilter.entries.toSet(),
    val showMailboxes: Boolean = true,
    val showDrinkingWater: Boolean = true,
    val showToilets: Boolean = true,
) {
    val isDefault: Boolean get() = this == PoiFilters()

    fun matches(poi: Poi): Boolean = when (poi.type) {
        PoiType.RECYCLING -> recyclingMaterials.isNotEmpty() && (
            recyclingMaterials.size == RecyclingFilter.entries.size ||
                recyclingMaterials.any { f -> poi.materials.any { it in f.materials } }
            )
        PoiType.POST_BOX -> showMailboxes
        PoiType.DRINKING_WATER -> showDrinkingWater
        PoiType.TOILET -> showToilets
    }
}
