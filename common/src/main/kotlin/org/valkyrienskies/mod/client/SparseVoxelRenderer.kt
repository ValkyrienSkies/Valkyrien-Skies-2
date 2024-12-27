package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.debug.DebugRenderer
import net.minecraft.world.phys.AABB
import org.valkyrienskies.core.util.datastructures.SparseVoxelPosition

class SparseVoxelRenderer() {
    val voxels = HashSet<SparseVoxelPosition>()

    init {
        voxels.add(SparseVoxelPosition(0, 128, 0, 2))
    }

    fun render(ms: PoseStack, buffer: BufferSource, camX: Double, camY: Double, camZ: Double) {
        for (voxel in voxels) {
            drawVoxel(voxel, ms, buffer, camX, camY, camZ)
        }
    }

    fun drawVoxel(voxel: SparseVoxelPosition, poseStack: PoseStack, buffer: BufferSource, camX: Double, camY: Double, camZ: Double) {
        poseStack.pushPose()

        // Draw the voxel
        val random = Minecraft.getInstance().level?.random ?: return
        DebugRenderer.renderFilledBox(voxel.toAABB(-camX, -camY, -camZ), 1.0f, 1.0f, 0.5f, 0.5f)
        //Tesselator.getInstance().end()

        poseStack.popPose()
    }

    private fun SparseVoxelPosition.toAABB(offsetX: Double = 0.0, offsetY: Double = 0.0, offsetZ: Double = 0.0): AABB {
        return AABB(x.toDouble() + offsetX, y.toDouble() + offsetY, z.toDouble() + offsetZ,
            x.toDouble() + extent.toDouble() + offsetX, y.toDouble() + extent.toDouble() + offsetY, z.toDouble() + extent.toDouble() + offsetZ)

    }
}
