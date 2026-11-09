package org.valkyrienskies.mod.mixin.mod_compat.theatrical;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.imabad.theatrical.blockentities.light.MovingLightBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(targets = {"dev.imabad.theatrical.client.blockentities.MovingLightRenderer"})
public class MixinMovingLightRenderer {

    /**
     * Account for ship rotation when rendering beam
     */
    @Inject(
        method = "preparePoseStack(Ldev/imabad/theatrical/blockentities/light/MovingLightBlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/core/Direction;FZLnet/minecraft/world/level/block/state/BlockState;Z)V",
        at = @At("HEAD")
    )
    private void injectPose(MovingLightBlockEntity blockEntity, PoseStack poseStack, Direction facing,
        float partialTicks, boolean isFlipped, BlockState blockState, boolean isHanging, CallbackInfo ci) {
        Level level = Minecraft.getInstance().level;
        // Don't remove this useless cast, without it, the mixin crashes somehow
        BlockPos pos = ((BlockEntity) blockEntity).getBlockPos();

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) return;

        poseStack.mulPose(new Quaternionf(ship.getTransform().getShipToWorldRotation()));
    }
}
