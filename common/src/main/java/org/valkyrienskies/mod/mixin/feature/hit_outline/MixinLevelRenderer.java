package org.valkyrienskies.mod.mixin.feature.hit_outline;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.render.batched.SplitShipRenderer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    @Nullable
    private ClientLevel level;

    @Shadow
    private static void renderShape(final PoseStack matrixStack, final VertexConsumer vertexConsumer,
        final VoxelShape voxelShape, final double d, final double e, final double f, final float red, final float green,
        final float blue, final float alpha) {
        throw new AssertionError();
    }

    /**
     * @reason mojank developers who wrote this don't quite understand what a matrixstack is apparently
     */
    @Inject(
        method = "renderHitOutline",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V",
            shift = Shift.BEFORE
        ),
        cancellable = true
    )
    private void preRenderShape(PoseStack matrixStack, VertexConsumer vertexConsumer, Entity entity,
        double camX, double camY, double camZ,
        BlockPos pos, BlockState state,
        CallbackInfo ci
    ) {
        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(this.level, pos);
        if (ship != null) {
            ci.cancel();
            if (!ShipRenderViews.shouldRenderShip(ship)) return;
            var pieces = VSGameUtilsKt.getAllShips(level).getSplitPieces(ship.getId());
            if (!pieces.isEmpty()) {
                SplitShipRenderer.drawOutline(pieces, state.getShape(level, pos, CollisionContext.of(entity)),
                    pos, matrixStack, vertexConsumer, camX, camY, camZ);
                return;
            }
            matrixStack.pushPose();
            transformRenderWithShip(ship.getRenderTransform(), matrixStack, pos, camX, camY, camZ);
            renderShape(matrixStack, vertexConsumer,
                state.getShape(this.level, pos, CollisionContext.of(entity)),
                0d, 0d, 0d, 0.0F, 0.0F, 0.0F, 0.4F);
            matrixStack.popPose();
        }
    }

}
