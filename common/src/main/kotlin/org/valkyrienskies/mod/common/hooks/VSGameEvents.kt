package org.valkyrienskies.mod.common.hooks

import com.mojang.blaze3d.vertex.PoseStack
import it.unimi.dsi.fastutil.objects.ObjectList
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo
import net.minecraft.client.renderer.RenderType
import org.joml.Matrix4f
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.impl.util.events.EventEmitterImpl

object VSGameEvents {

    val registriesCompleted = EventEmitterImpl<Unit>()
    val tagsAreLoaded = EventEmitterImpl<Unit>()

    val renderShip = EventEmitterImpl<ShipRenderEvent>()
    val postRenderShip = EventEmitterImpl<ShipRenderEvent>()
    val shipsStartRendering = EventEmitterImpl<ShipStartRenderEvent>()
    val renderShipSodium = EventEmitterImpl<ShipRenderEventSodium>()
    val postRenderShipSodium = EventEmitterImpl<ShipRenderEventSodium>()
    val shipsStartRenderingSodium = EventEmitterImpl<ShipStartRenderEventSodium>()


    data class ShipStartRenderEvent(
        val renderer: LevelRenderer,
        val renderType: RenderType,
        val poseStack: PoseStack,
        val camX: Double, val camY: Double, val camZ: Double,
        val projectionMatrix: Matrix4f
    )

    data class ShipRenderEvent(
        val renderer: LevelRenderer,
        val renderType: RenderType,
        val poseStack: PoseStack,
        val camX: Double, val camY: Double, val camZ: Double,
        val projectionMatrix: Matrix4f,
        val ship: ClientShip,
        val chunks: ObjectList<RenderChunkInfo>
    )

    data class ShipStartRenderEventSodium(
        val renderer: ChunkRenderer,
        val pass: TerrainRenderPass,
        val matrices: ChunkRenderMatrices,
        val camX: Double, val camY: Double, val camZ: Double
    )

    data class ShipRenderEventSodium(
        val renderer: ChunkRenderer,
        val pass: TerrainRenderPass,
        val matrices: ChunkRenderMatrices,
        val camX: Double, val camY: Double, val camZ: Double,
        val ship: ClientShip,
        val chunks: SortedRenderLists
    )
}

