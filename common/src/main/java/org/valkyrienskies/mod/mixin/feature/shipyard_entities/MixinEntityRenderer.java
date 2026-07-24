package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    /**
     * Entities are rendered along with their name tags, which is good as the ship transforms are already applied.
     * For readability, however, we want the tags to always be vertically oriented so we negate rotation of the ship.
     * We inject specifically after translation as offsetting the tag vertically in shipspace makes more sense.
     */
    @Inject(
        method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
            shift = At.Shift.AFTER
        )
    )
    private void revertShipRotation(Entity entity, Component component, PoseStack matrices, MultiBufferSource bufferSource, int packedLight,
        CallbackInfo ci) {
        final ClientShip ship = (ClientShip)VSGameUtilsKt.getLoadedShipManagingPos(entity.level(), entity.blockPosition());
        if (ship != null) {
            matrices.mulPose(new Quaternionf(ship.getRenderTransform().getShipToWorldRotation()).invert());
        }
    }
}
