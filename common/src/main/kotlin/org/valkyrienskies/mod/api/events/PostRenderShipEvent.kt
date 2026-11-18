package org.valkyrienskies.mod.api.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import org.jetbrains.annotations.ApiStatus.Experimental
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.api.render.ShipRenderView
import org.valkyrienskies.mod.api.render.ShipGeometryMask

@Experimental
interface PostRenderShipEvent {
    val ship: ClientShip
    val view: ShipRenderView

    /** Submit vertices in shipyard block coordinates relative to this origin. */
    val origin: BlockPos
    val poseStack: PoseStack
    val buffers: MultiBufferSource

    /** Request while the view's scene framebuffer is bound. Available for batched terrain with single-sample scene depth. */
    fun geometryMask(): ShipGeometryMask? = null
}
