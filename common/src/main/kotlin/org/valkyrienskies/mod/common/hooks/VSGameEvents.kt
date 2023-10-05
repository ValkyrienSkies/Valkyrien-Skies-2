package org.valkyrienskies.mod.common.hooks

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Matrix4f
import it.unimi.dsi.fastutil.objects.ObjectList
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo
import net.minecraft.client.renderer.RenderType
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.impl.util.events.EventEmitterImpl
import java.util.concurrent.atomic.AtomicReference

object VSGameEvents {

    val registriesCompleted = EventEmitterImpl<Unit>()
    val tagsAreLoaded = EventEmitterImpl<Unit>()

    val renderShip = EventEmitterImpl<ShipRenderEvent>()
    val renderShipModifiable = EventEmitterImpl<ModifiableShipRenderEvent>()
    val postRenderShip = EventEmitterImpl<ShipRenderEvent>()
    val shipsStartRendering = EventEmitterImpl<ShipStartRenderEvent>()

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
        val projectionMatrix: AtomicReference<Matrix4f>,
        val center: AtomicReference<Vector3dc>,
        val ship: ClientShip,
        val chunks: ObjectList<RenderChunkInfo>
    )

}

