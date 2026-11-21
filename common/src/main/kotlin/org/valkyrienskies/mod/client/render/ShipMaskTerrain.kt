package org.valkyrienskies.mod.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4f
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.api.render.ShipGeometryMask
import org.valkyrienskies.mod.common.render.batched.ShipMaskTarget

internal class ShipMaskTerrain(pose: PoseStack, projection: Matrix4f, val x: Double, val y: Double, val z: Double) {
    val modelView = Matrix4f(pose.last().pose())
    val projection = Matrix4f(projection)
    val atlas = RenderSystem.getShaderTexture(0)
    val lightmap = RenderSystem.getShaderTexture(2)
    val color = RenderSystem.getShaderColor().clone()
}

internal fun interface ShipTerrainMaskRenderer {
    fun render(target: ShipMaskTarget, ship: ClientShip, terrain: ShipMaskTerrain): ShipGeometryMask?
}
