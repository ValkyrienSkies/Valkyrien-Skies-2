package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape.INVISIBLE
import net.minecraft.world.level.block.RenderShape.MODEL
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import java.util.Random

class PhysicsEmptyRenderer(context: EntityRendererProvider.Context) : EntityRenderer<VSPhysicsEntity>(context) {
    override fun render(
        fallingBlockEntity: VSPhysicsEntity, f: Float, g: Float, poseStack: PoseStack,
        multiBufferSource: MultiBufferSource, i: Int
    ) {
        val blockState = Blocks.DARK_OAK_WOOD.defaultBlockState()
        if (blockState.renderShape != MODEL) {
            return
        }
        val level = fallingBlockEntity.getLevel()
        if (blockState === level.getBlockState(
                fallingBlockEntity.blockPosition()
            ) || blockState.renderShape == INVISIBLE
        ) {
            return
        }
        poseStack.pushPose()
        val blockPos = BlockPos(fallingBlockEntity.x, fallingBlockEntity.boundingBox.maxY, fallingBlockEntity.z)
        poseStack.translate(-0.5, 0.0, -0.5)
        val blockRenderDispatcher = Minecraft.getInstance().blockRenderer
        blockRenderDispatcher.modelRenderer.tesselateBlock(
            level, blockRenderDispatcher.getBlockModel(blockState), blockState, blockPos, poseStack,
            multiBufferSource.getBuffer(
                ItemBlockRenderTypes.getMovingBlockRenderType(blockState)
            ), false, Random(), blockState.getSeed(BlockPos.ZERO), OverlayTexture.NO_OVERLAY
        )
        poseStack.popPose()
        super.render(fallingBlockEntity, f, g, poseStack, multiBufferSource, i)
    }

    override fun getTextureLocation(entity: VSPhysicsEntity): ResourceLocation {
        return TextureAtlas.LOCATION_BLOCKS
    }
}
