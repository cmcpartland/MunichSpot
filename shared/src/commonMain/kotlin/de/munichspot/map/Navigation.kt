package de.munichspot.map

import de.munichspot.data.Poi

/** URI that opens the platform maps app with directions to [poi]. */
expect fun navigationUri(poi: Poi): String
