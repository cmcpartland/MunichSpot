package de.munichspot.map

import de.munichspot.data.PoiType

internal fun PoiType.hasFilters(): Boolean = this == PoiType.RECYCLING || this == PoiType.TOILET
