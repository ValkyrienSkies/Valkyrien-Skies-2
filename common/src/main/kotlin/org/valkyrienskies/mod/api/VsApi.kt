package org.valkyrienskies.mod.api

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

import org.jetbrains.annotations.ApiStatus.*
import org.joml.Vector3dc
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.VsCoreApi
import org.valkyrienskies.core.api.event.ListenableEvent
import org.valkyrienskies.core.api.ships.*
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.ClientShipWorld
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
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

    /**
     * Returns the [DimensionId]
     */
    fun getDimensionId(level: Level): DimensionId

    /**
     * Returns the ship that [entity] is mounted to, if it exists.
     */
    fun getShipMountedTo(entity: Entity?): Ship?

    /**
     * Returns the position in the ship that the [entity] is mounted to, if
     * it exists.
     */
    fun getMountPosInShip(entity: Entity?): Vector3dc?

    /**
     * Returns the position in the ship that the [entity] is mounted to, if
     * it exists, interpolating their position using [partialTicks]
     */
    fun getMountPosInShip(entity: Entity?, partialTicks: Float): Vector3dc?

    /**
     * Returns the [ServerShipWorld] associated with the given [MinecraftServer]
     * if it exists.
     *
     * This will return null if there is no [ServerShipWorld] associated with
     * [server]
     */
    fun getServerShipWorld(server: MinecraftServer?): ServerShipWorld?

    /**
     * Returns the [ClientShipWorld] associated with the given [Minecraft]
     * if it exists.
     *
     * This will return null if there is no [ClientShipWorld] associated with
     * [client]
     */
    fun getClientShipWorld(client: Minecraft?): ClientShipWorld?

    /**
     * Returns the [ShipWorld] associated with the given [Level] if it exists.
     *
     * This will return null if there is no [ShipWorld] associated with [level].
     */
    fun getShipWorld(level: Level?): ShipWorld?

    /**
     * Potentially returns the globally unique [ServerShipWorld] if it exists.
     *
     * This is not guaranteed to always work. Prefer to use
     * `getServerShipWorld(MinecraftServer)` in almost all cases.
     *
     * This will return null if no [ServerShipWorld] is currently loaded, or if
     * multiple are loaded because multiple [MinecraftServer] are loaded.
     */
    @Experimental
    fun getServerShipWorld(): ServerShipWorld?

    /**
     * Potentially returns the globally unique [ClientShipWorld] if it exists.
     *
     * This is not guaranteed to always work. Prefer to use
     * `getClientShipWorld(Minecraft)` in almost all cases.
     *
     * This will return null if no [ClientShipWorld] is currently loaded, or if
     * multiple are loaded because multiple [Minecraft] are loaded.
     */
    @Experimental
    fun getClientShipWorld(): ClientShipWorld?

    /**
     * Returns true if the chunk is in the shipyard.
     *
     * If [level] is null, always returns false.
     */
    fun isChunkInShipyard(level: Level?, chunkX: Int, chunkZ: Int): Boolean

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

    /**
     * Returns the ship whose shipyard contains this chunk, if it exists and is
     * in [level].
     *
     * If [level] is a [ServerLevel], this will return a [ServerShip].
     * If [level] is a [ClientLevel], this will return a [ClientShip].
     *
     * @param level The [Level] to look for the ship in.
     */
    fun getShipManagingChunk(level: Level?, pos: ChunkPos?): Ship?

    fun getShipManagingChunk(level: Level?, chunkX: Int, chunkZ: Int): Ship?

    fun getShipsIntersecting(level: Level?, aabb: AABBdc?): Iterable<Ship>

    fun getShipsIntersecting(level: Level?, x: Double, y: Double, z: Double): Iterable<Ship>
}
