package de.munichspot.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.munichspot.data.PlaceKind
import de.munichspot.data.PoiType
import de.munichspot.shared.resources.*
import de.munichspot.shared.resources.Res
import org.jetbrains.compose.resources.stringResource

internal fun PoiType.hasFilters(): Boolean = this == PoiType.RECYCLING || this == PoiType.RECYCLING_CENTRE || this == PoiType.TOILET

internal fun PoiType.pinColor(): Color = when (this) {
    PoiType.RECYCLING -> RecyclingGreen
    PoiType.CLOTHES -> ClothesRed
    PoiType.RECYCLING_CENTRE -> RecyclingCentreTeal
    PoiType.POST_BOX -> PostBoxYellow
    PoiType.DRINKING_WATER -> DrinkingWaterBlue
    PoiType.TOILET -> ToiletPurple
}

@Composable
internal fun PoiType.label(): String = stringResource(
    when (this) {
        PoiType.RECYCLING -> Res.string.type_recycling
        PoiType.CLOTHES -> Res.string.type_clothes
        PoiType.RECYCLING_CENTRE -> Res.string.type_recycling_centre
        PoiType.POST_BOX -> Res.string.type_post_box
        PoiType.DRINKING_WATER -> Res.string.type_drinking_water
        PoiType.TOILET -> Res.string.type_toilet
    },
)

@Composable
internal fun PlaceKind.label(): String = stringResource(
    when (this) {
        PlaceKind.STREET -> Res.string.kind_street
        PlaceKind.PARK -> Res.string.kind_park
        PlaceKind.SQUARE -> Res.string.kind_square
        PlaceKind.STATION -> Res.string.kind_station
        PlaceKind.LANDMARK -> Res.string.kind_landmark
        PlaceKind.DISTRICT -> Res.string.kind_district
    },
)

internal val DrinkingWaterText = Color(0xFF01579B)

private val RecyclingGreen = Color(0xFF2E7D32)
private val RecyclingCentreTeal = Color(0xFF00695C)
private val ClothesRed = Color(0xFFC62828)
private val PostBoxYellow = Color(0xFFF9A825)
private val DrinkingWaterBlue = Color(0xFF81D4FA)
private val ToiletPurple = Color(0xFF8E24AA)
internal val WarningRed = Color(0xFFD32F2F)
