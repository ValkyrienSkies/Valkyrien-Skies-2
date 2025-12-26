package org.valkyrienskies.mod.common.hooks

import com.mojang.blaze3d.vertex.PoseStack
import it.unimi.dsi.fastutil.objects.ObjectList
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.level.entity.EntityAccess
import net.minecraft.world.level.entity.EntitySection
import org.joml.Matrix4f
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.util.events.EventEmitterImpl
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.config.ConfigType

object VSGameEvents {

    val registriesCompleted = EventEmitterImpl<Unit>()
    val tagsAreLoaded = EventEmitterImpl<Unit>()

    /** Emits a Set of config entries that were updated **/
    val configUpdated = EventEmitterImpl<Set<ConfigUpdateEntry>>()

    data class ConfigUpdateEntry(
        val configType: ConfigType,
        val category: List<String>,
        val name: String
    ) {
        val path: String get() = (category + name).joinToString(".")
    }

    val renderShip = EventEmitterImpl<ShipRenderEvent>()
    val postRenderShip = EventEmitterImpl<ShipRenderEvent>()
    val entitySectionSetShip = EventEmitterImpl<EntitySectionSetShip>()
    val shipsStartRendering = EventEmitterImpl<ShipStartRenderEvent>()
    val renderShipSodium = EventEmitterImpl<ShipRenderEventSodium>()
    val postRenderShipSodium = EventEmitterImpl<ShipRenderEventSodium>()
    val shipsStartRenderingSodium = EventEmitterImpl<ShipStartRenderEventSodium>()

    data class EntitySectionSetShip(
        val section: EntitySection<EntityAccess>,
        val ship: Ship
    )

    val shipSplit = EventEmitterImpl<ShipSplitEvent>()

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

    data class ShipSplitEvent(
        val ship: ShipId,
        val newShip: ShipId,
        val blocks: DenseBlockPosSet
    )

    data class ShipStartRenderEventSodium(
        val pass: TerrainRenderPass,
        val matrices: ChunkRenderMatrices,
        val camX: Double, val camY: Double, val camZ: Double
    )

    data class ShipRenderEventSodium(
        val pass: TerrainRenderPass,
        val matrices: ChunkRenderMatrices,
        val camX: Double, val camY: Double, val camZ: Double,
        val ship: ClientShip,
        val chunks: SortedRenderLists
    )
}

