package org.valkyrienskies.mod.common.assembly

import net.minecraft.server.level.ServerLevel
import org.jetbrains.annotations.ApiStatus
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import java.util.function.Supplier
//TODO refine docs
//TODO finish
//TODO move to .api?
/**
 * Should be saved before any ship blocks is saved
 * Should be loaded after all ships were created and all blocks were placed and loaded
 */
@ApiStatus.Experimental
interface ICopyableAttachment {
    /**
     * Should be called before jackson serialization
     */
    fun onCopy(
        level: Supplier<ServerLevel>,
        shipOn: LoadedServerShip,
        shipsToBeSaved: List<ServerShip>,
        centerPositions: Map<ShipId, Vector3dc>
    )

    fun onAfterCopy(
        level: Supplier<ServerLevel>,
        shipOn: LoadedServerShip,
        shipsToBeSaved: List<ServerShip>,
        centerPositions: Map<ShipId, Vector3dc>
    ) {}

    /**
     * Should be called after jackson deserialization
     */
    fun onPaste(
        level: Supplier<ServerLevel>,
        shipOn: LoadedServerShip,
        loadedShips: Map<Long, ServerShip>,
        centerPositions: Map<ShipId, Pair<Vector3dc, Vector3dc>>
    )
}
