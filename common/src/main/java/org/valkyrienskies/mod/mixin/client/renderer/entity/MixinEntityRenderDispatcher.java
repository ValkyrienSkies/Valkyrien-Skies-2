package org.valkyrienskies.mod.mixin.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE))
    void render(
        final Entity entity, final double x, final double y, final double z,
        final float rotationYaw, final float partialTicks,
        final PoseStack matrixStack,
        final MultiBufferSource buffer, final int packedLight, final CallbackInfo ci) {

        final ClientShip ship =
            (ClientShip) VSGameUtilsKt.getShipObjectManagingPos(entity.level, entity.blockPosition());
        if (ship != null) {
            VSEntityManager.INSTANCE.getHandler(entity.getType())
                .manipulateRenderMatrix(ship, entity, matrixStack, partialTicks);
        }
    }

    @Inject(
        method = "shouldRender",
        at = @At("TAIL"),
        cancellable = true
    )
    void shouldRender(final Entity entity, final Frustum frustum,
        final double camX, final double camY, final double camZ,
        final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getShipObjectManagingPos(entity.level, entity.blockPosition());
            if (ship != null) {
                // TODO actually check
                cir.setReturnValue(true);
            }
        }
    }

}
