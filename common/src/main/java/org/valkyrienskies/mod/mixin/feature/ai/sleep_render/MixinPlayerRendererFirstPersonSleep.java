package org.valkyrienskies.mod.mixin.feature.ai.sleep_render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Hide head/hat for the local player viewing first-person while sleeping in a ship-mounted bed; vanilla's first-person normally culls them via the camera-inside-head trick, but our ship-mounted sleep render places the body offset from the camera so the head ends up visible.
@Mixin(value = PlayerRenderer.class, priority = 600)
public abstract class MixinPlayerRendererFirstPersonSleep
    extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private MixinPlayerRendererFirstPersonSleep(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> playerModel, float f) {
        super(context, playerModel, f);
        throw new AssertionError();
    }

    // INVOKE shift = BEFORE on the super render call: vanilla PlayerRenderer.render's preceding setModelProperties does setAllVisible(true), so we have to set head.visible = false after that and right before the model is drawn.
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE))
    private void vs$hideHeadForFirstPersonShipBedSleep(
        final AbstractClientPlayer player, final float entityYaw, final float partialTicks,
        final PoseStack poseStack, final MultiBufferSource buffer, final int packedLight,
        final CallbackInfo ci
    ) {
        if (!vs$shouldHideHead(player, partialTicks)) return;
        final PlayerModel<AbstractClientPlayer> model = this.getModel();
        model.head.visible = false;
        model.hat.visible = false;
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN"))
    private void vs$restoreHeadAfterFirstPersonShipBedSleep(
        final AbstractClientPlayer player, final float entityYaw, final float partialTicks,
        final PoseStack poseStack, final MultiBufferSource buffer, final int packedLight,
        final CallbackInfo ci
    ) {
        if (!vs$shouldHideHead(player, partialTicks)) return;
        // Model parts are shared instances — restore so other player renders (third-person, other clients) aren't affected. HAT visibility is user-configurable; restore from PlayerModelPart preference.
        final PlayerModel<AbstractClientPlayer> model = this.getModel();
        model.head.visible = true;
        model.hat.visible = player.isModelPartShown(PlayerModelPart.HAT);
    }

    @Unique
    private static boolean vs$shouldHideHead(final AbstractClientPlayer player, final float partialTicks) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != player) return false;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return false;
        if (!player.isSleeping()) return false;
        return VSGameUtilsKt.getShipMountedToData(player, partialTicks) != null;
    }
}
