package org.valkyrienskies.mod.common.command

import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId

data class ShipSelector(
    val slug: String? = null,
    val id: ShipId? = null,
    val maxAmount: Int = Int.MAX_VALUE
) {

    fun select(queryableShipData: QueryableShipData<Ship>): Set<Ship> {
        var found = queryableShipData.asSequence()

        slug?.let { slug -> found = found.filter { it.slug == slug } }
        id?.let { id -> found = found.filter { it.id == id } }

        return found.take(maxAmount).toSet()
    }
}

