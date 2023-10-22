package org.valkyrienskies.mod.api_impl.events

import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.util.events.EventEmitterImpl
import org.valkyrienskies.mod.api.VsApi
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.events.PreRenderShipEvent
import org.valkyrienskies.mod.api.events.RegisterBlockStateEvent
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.compat.clothconfig.VSClothConfig

class VsApiImpl : VsApi {

    override val registerBlockStateEvent = EventEmitterImpl<RegisterBlockStateEvent>()
    override val preRenderShipEvent = EventEmitterImpl<PreRenderShipEvent>()
    override val postRenderShipEvent = EventEmitterImpl<PostRenderShipEvent>()

    override fun isShipMountingEntity(entity: Entity): Boolean {
        return entity is ShipMountingEntity
    }

    override fun createConfigScreenLegacy(parent: Screen, vararg configs: Class<*>): Screen {
        return VSClothConfig.createConfigScreenFor(parent, *configs)
    }


    override fun getShipManagingBlock(level: Level?, pos: BlockPos?): Ship? {
        return pos?.let { level?.getShipManagingPos(it) }
    }

    override fun getShipManagingChunk(level: Level?, pos: ChunkPos?): Ship? {
        return pos?.let { level?.getShipManagingPos(it) }
    }
}
