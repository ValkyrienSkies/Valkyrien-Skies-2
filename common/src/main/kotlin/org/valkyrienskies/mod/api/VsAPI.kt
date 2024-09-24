package org.valkyrienskies.mod.api

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

import org.jetbrains.annotations.ApiStatus.*
import org.valkyrienskies.core.api.VsCoreApi
import org.valkyrienskies.core.api.event.ListenableEvent
import org.valkyrienskies.core.api.ships.*
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.events.PreRenderShipEvent
import org.valkyrienskies.mod.api.events.RegisterBlockStateEvent

@NonExtendable
interface VsApi : VsCoreApi {

    /**
     * This event gets called when it's time to register physics block states for Minecraft block states.
     */
    @get:Experimental
    val registerBlockStateEvent: ListenableEvent<RegisterBlockStateEvent>

    @get:Experimental
    val preRenderShipEvent: ListenableEvent<PreRenderShipEvent>

    @get:Experimental
    val postRenderShipEvent: ListenableEvent<PostRenderShipEvent>

    fun isShipMountingEntity(entity: Entity): Boolean

    @Deprecated(message = "The legacy VS config system will be replaced soon. " +
        "Migrate to another config library, or the new system when it's released. ")
    fun createConfigScreenLegacy(parent: Screen, vararg configs: Class<*>): Screen

    /**
     * Get the ship with the chunk claim that contains [pos], if it exists.
     *
     * If either parameter is null, this will return null.
     *
     * @param level The [Level] to look for the ship in. If [level] is a
     * [ServerLevel], this will return a [ServerShip]. If [level] is a
     * [ClientLevel], this will return a [ClientShip].
     *
     * @param pos A block position in the Shipyard
     */
    fun getShipManagingBlock(level: Level?, pos: BlockPos?): Ship?

    fun getShipManagingChunk(level: Level?, pos: ChunkPos?): Ship?

    fun getShipManagingChunk(level: Level?, chunkX: Int, chunkZ: Int): Ship?
}
