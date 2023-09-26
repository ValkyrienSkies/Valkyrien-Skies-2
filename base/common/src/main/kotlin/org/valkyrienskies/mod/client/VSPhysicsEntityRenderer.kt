package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.block.RenderShape.INVISIBLE
import net.minecraft.world.level.block.RenderShape.MODEL
import org.valkyrienskies.core.impl.game.ships.ShipObjectClientWorld
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.util.toMinecraft
import java.util.Random

class VSPhysicsEntityRenderer(context: EntityRendererProvider.Context) : EntityRenderer<VSPhysicsEntity>(context) {
    override fun render(
        fallingBlockEntity: VSPhysicsEntity, f: Float, partialTick: Float, poseStack: PoseStack,
        multiBufferSource: MultiBufferSource, i: Int
    ) {
        val blockState = ValkyrienSkiesMod.TEST_SPHERE.defaultBlockState()
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

        val renderTransform = fallingBlockEntity.getRenderTransform(
            ((Minecraft.getInstance() as IShipObjectWorldClientProvider).shipObjectWorld as ShipObjectClientWorld)
        ) ?: return

        val expectedX = fallingBlockEntity.xo + (fallingBlockEntity.x - fallingBlockEntity.xo) * partialTick
        val expectedY = fallingBlockEntity.yo + (fallingBlockEntity.y - fallingBlockEntity.yo) * partialTick
        val expectedZ = fallingBlockEntity.zo + (fallingBlockEntity.z - fallingBlockEntity.zo) * partialTick

        // Replace the default transform applied by mc with these offsets
        val offsetX = renderTransform.positionInWorld.x() - expectedX
        val offsetY = renderTransform.positionInWorld.y() - expectedY
        val offsetZ = renderTransform.positionInWorld.z() - expectedZ

        poseStack.pushPose()
        val blockPos = BlockPos(fallingBlockEntity.x, fallingBlockEntity.boundingBox.maxY, fallingBlockEntity.z)

        poseStack.translate(offsetX, offsetY, offsetZ)
        poseStack.mulPose(renderTransform.shipToWorldRotation.toMinecraft())
        poseStack.translate(-0.5, -0.5, -0.5)
        val blockRenderDispatcher = Minecraft.getInstance().blockRenderer
        blockRenderDispatcher.modelRenderer.tesselateBlock(
            level, blockRenderDispatcher.getBlockModel(blockState), blockState, blockPos, poseStack,
            multiBufferSource.getBuffer(
                ItemBlockRenderTypes.getMovingBlockRenderType(blockState)
            ), false, Random(), blockState.getSeed(BlockPos.ZERO), OverlayTexture.NO_OVERLAY
        )
        poseStack.popPose()
        super.render(fallingBlockEntity, f, partialTick, poseStack, multiBufferSource, i)
    }

    override fun getTextureLocation(entity: VSPhysicsEntity): ResourceLocation {
        return InventoryMenu.BLOCK_ATLAS
    }
}
