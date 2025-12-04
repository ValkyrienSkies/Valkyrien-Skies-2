package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SmartBlockEntityRenderer.class)
public class MixinSmartBlockEntityRenderer {
    @WrapOperation(
        method = "renderNameplateOnHover",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"
        )
    )
    private <T extends SmartBlockEntity> void revertShipRotation(PoseStack matrices, double d, double e, double f,
        Operation<Void> original, @Local(argsOnly = true) T entity) {
        original.call(matrices, d, e, f);
        final ClientShip ship = (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.getLevel(), entity.getBlockPos());
        if (ship != null) {
            matrices.mulPose(new Quaternionf(ship.getRenderTransform().getShipToWorldRotation()).invert());
        }
    }
}
