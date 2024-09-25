package org.valkyrienskies.mod.common.hooks

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Matrix4f
import it.unimi.dsi.fastutil.objects.ObjectList
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.util.events.EventEmitterImpl
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet

object VSGameEvents {

    val registriesCompleted = EventEmitterImpl<Unit>()
    val tagsAreLoaded = EventEmitterImpl<Unit>()

    val renderShip = EventEmitterImpl<ShipRenderEvent>()
    val postRenderShip = EventEmitterImpl<ShipRenderEvent>()
    val shipsStartRendering = EventEmitterImpl<ShipStartRenderEvent>()

    val shipSplit = EventEmitterImpl<ShipSplitEvent>()

    val itemTooltips = EventEmitterImpl<ItemTooltipsEvent>()

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

    data class ItemTooltipsEvent(
        val itemStack: ItemStack,
        val level: Level,
        val list: MutableList<Component>,
        val tooltipFlag: TooltipFlag,
    )
}

