package org.valkyrienskies.mod.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4f

internal class ShipMaskTerrain(pose: PoseStack, projection: Matrix4f, val x: Double, val y: Double, val z: Double) {
    val modelView = Matrix4f(pose.last().pose())
    val projection = Matrix4f(projection)
    val atlas = RenderSystem.getShaderTexture(0)
    val lightmap = RenderSystem.getShaderTexture(2)
    val color = RenderSystem.getShaderColor().clone()
}
