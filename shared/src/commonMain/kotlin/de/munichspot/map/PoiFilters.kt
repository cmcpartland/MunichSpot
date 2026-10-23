package de.munichspot.map

import de.munichspot.data.Material
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import de.munichspot.data.isOpen
import kotlinx.datetime.LocalDateTime

internal enum class RecyclingFilter(val materials: Set<Material>) {
    GLASS(setOf(Material.GLASS_BROWN, Material.GLASS_GREEN, Material.GLASS_WHITE)),
    PACKAGING(setOf(Material.PACKAGING)),
}

internal enum class ToiletFilter { ACCESSIBLE, FREE, CHANGING, OPEN_NOW }

internal enum class CentreFilter { OPEN_NOW }

internal data class PoiFilters(
    val recyclingMaterials: Set<RecyclingFilter> = RecyclingFilter.entries.toSet(),
    val toiletRequirements: Set<ToiletFilter> = emptySet(),
    val centreRequirements: Set<CentreFilter> = emptySet(),
) {
    fun isDefault(type: PoiType): Boolean = reset(type) == this

    fun reset(type: PoiType): PoiFilters = when (type) {
        PoiType.RECYCLING -> copy(recyclingMaterials = PoiFilters().recyclingMaterials)
        PoiType.TOILET -> copy(toiletRequirements = emptySet())
        PoiType.RECYCLING_CENTRE -> copy(centreRequirements = emptySet())
        else -> this
    }

    fun matches(poi: Poi, now: LocalDateTime): Boolean = when (poi.type) {
        PoiType.RECYCLING -> recyclingMaterials.isNotEmpty() && (
            recyclingMaterials.size == RecyclingFilter.entries.size ||
                recyclingMaterials.any { f -> poi.materials.any { it in f.materials } }
            )
        PoiType.RECYCLING_CENTRE -> CentreFilter.OPEN_NOW !in centreRequirements || !poi.knownClosed(now)
        PoiType.CLOTHES, PoiType.POST_BOX, PoiType.DRINKING_WATER -> true
        PoiType.TOILET -> toiletRequirements.all {
            val toilet = poi.toilet
            when (it) {
                ToiletFilter.ACCESSIBLE -> toilet?.accessible == true
                ToiletFilter.FREE -> toilet?.price == 0.0
                ToiletFilter.CHANGING -> toilet?.changing == true
                ToiletFilter.OPEN_NOW -> !poi.knownClosed(now)
            }
        }
    }
}

/** True only when the poi has a schedule and it says closed; unknown hours never count as closed. */
internal fun Poi.knownClosed(now: LocalDateTime): Boolean = schedule?.isOpen(now) == false
