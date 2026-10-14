package de.munichspot.map

import de.munichspot.data.PoiType

internal fun PoiType.keywords(): List<String> = when (this) {
    PoiType.RECYCLING -> listOf("wertstoff", "recycling", "glas", "container", "verpackung", "müll")
    PoiType.CLOTHES -> listOf("altkleider", "kleidung", "kleider", "klamotten", "textil", "schuhe", "spende", "clothes")
    PoiType.POST_BOX -> listOf("briefkasten", "briefkästen", "post", "brief", "mailbox")
    PoiType.DRINKING_WATER -> listOf("trinkbrunnen", "brunnen", "wasser", "trinkwasser", "fountain")
    PoiType.TOILET -> listOf("toilette", "wc", "klo", "toilet", "restroom")
}

internal fun PoiType.hasFilters(): Boolean = this == PoiType.RECYCLING || this == PoiType.TOILET

internal fun PoiType.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    return q.isEmpty() || label().lowercase().contains(q) || keywords().any { it.contains(q) }
}
