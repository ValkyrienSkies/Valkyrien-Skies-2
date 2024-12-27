package org.valkyrienskies.mod.api

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.jetbrains.annotations.ApiStatus.*
import org.joml.Vector3dc
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.VsCoreApi
import org.valkyrienskies.core.api.event.ListenableEvent
import org.valkyrienskies.core.api.ships.*
import org.valkyrienskies.core.api.world.ClientShipWorld
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.events.PreRenderShipEvent
import org.valkyrienskies.mod.api.events.RegisterBlockStateEvent

/**
 * Public API for Valkyrien Skies with both Minecraft and core APIs. This class
 * is stable to use, but using the extension methods (exposed as static methods
 * in `ValkyrienSkies` class to Java users) is more ergonomic in most cases.
 *
 * This is meant to be used by:
 * - Valkyrien Skies addon developers
 * - 3rd-party mod developers implementing Valkyrien Skies compatibility or
 *   integrations
 *
 * You can access the singleton instance of this via [vsApi]
 * (exposed as `ValkyrienSkies.getApi()` from Java).
 */
/*
 * This class may be moved into a separate mod at some point, so that it can
 * be shaded. Therefore, only use Minecraft classes and classes in
 *
 * - org.valkyrienskies.core.api.*
 * - org.valkyrienskies.mod.api.*
 * - org.joml.*
 */
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


    @Deprecated(message = "The legacy VS config system will be replaced soon. " +
        "Migrate to another config library, or the new system when it's released. ")
    fun createConfigScreenLegacy(parent: Screen, vararg configs: Class<*>): Screen

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
     * Returns the ship whose shipyard contains this chunk, if it exists and is
     * in [level].
     *
     * If [level] is a [ServerLevel], this will return a [ServerShip].
     * If [level] is a [ClientLevel], this will return a [ClientShip].
     *
     * @param level The [Level] to look for the ship in.
     */
    fun getShipManagingChunk(level: Level?, chunkX: Int, chunkZ: Int): Ship?

    fun getShipManagingChunk(level: ClientLevel?, chunkX: Int, chunkZ: Int): ClientShip?

    fun getShipManagingChunk(level: ServerLevel?, chunkX: Int, chunkZ: Int): ServerShip?

    fun getShipsIntersecting(level: Level?, aabb: AABBdc?): Iterable<Ship>

    fun getShipsIntersecting(level: Level?, x: Double, y: Double, z: Double): Iterable<Ship>
}
