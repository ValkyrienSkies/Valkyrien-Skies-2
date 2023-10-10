package org.valkyrienskies.mod.common.hooks

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Matrix4f
import it.unimi.dsi.fastutil.objects.ObjectList
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.apache.commons.lang3.mutable.MutableObject
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.impl.util.events.EventEmitterImpl

object VSGameEvents {

    val registriesCompleted = EventEmitterImpl<Unit>()
    val tagsAreLoaded = EventEmitterImpl<Unit>()

    val renderShip = EventEmitterImpl<ShipRenderEvent>()
    val renderShipModifiable = EventEmitterImpl<ModifiableShipRenderEvent>()
    val postRenderShip = EventEmitterImpl<ShipRenderEvent>()
    val shipsStartRendering = EventEmitterImpl<ShipStartRenderEvent>()
    val shipyardEntityRenderTransform =
        EventEmitterImpl<PostShipyardEntityRenderTransformEvent<*>>()

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

    data class ModifiableShipRenderEvent(
        val renderer: LevelRenderer,
        val renderType: RenderType,
        val poseStack: PoseStack,
        val camX: Double, val camY: Double, val camZ: Double,
        val projectionMatrix: MutableObject<Matrix4f>,
        val center: MutableObject<Vector3dc>,
        val ship: ClientShip,
        val chunks: ObjectList<RenderChunkInfo>
    )

    data class PostShipyardEntityRenderTransformEvent<T: Entity>(
        val ship: ClientShip,
        val entity: T,
        val entityRenderer: EntityRenderer<T>,
        val x: Double, val y: Double, val z: Double,
        val rotationYaw: Float, val partialTicks: Float,
        val matrixStack: PoseStack,
        val buffer: MultiBufferSource,
        val packedLight: Int
    )

}

