// package org.valkyrienskies.mod.mixin.mod_compat.create.client;

// import com.mojang.blaze3d.vertex.PoseStack;
// import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
// import com.simibubi.create.content.contraptions.Contraption;
// import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
// import com.simibubi.create.content.contraptions.render.ContraptionRenderInfo;
// import net.createmod.catnip.animation.AnimationTickHolder;
// import org.valkyrienskies.mod.mixin.mod_compat.create.accessors.ContraptionMatricesAccessor;
// import net.minecraft.util.Mth;
// import org.spongepowered.asm.mixin.Final;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// import org.valkyrienskies.core.api.ships.ClientShip;
// import org.valkyrienskies.mod.common.VSClientGameUtils;
// import org.valkyrienskies.mod.common.VSGameUtilsKt;

// @Mixin(ContraptionRenderInfo.class)
// public class MixinContraptionRenderInfo {

//     @Shadow
//     @Final
//     public Contraption contraption;
//     @Shadow
//     @Final
//     private ContraptionMatrices matrices;

//     @Inject(method = "setupMatrices", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"), cancellable = true)
//     private void injectAndDoTransformation(PoseStack viewProjection, double camX, double camY, double camZ, CallbackInfo ci) {
//         AbstractContraptionEntity entity = contraption.entity;

//         if (VSGameUtilsKt.getShipManaging(entity) instanceof final ClientShip ship) {
//             viewProjection.pushPose();

//             double partialTick = AnimationTickHolder.getPartialTicks();
//             VSClientGameUtils.transformRenderWithShip(
//                     ship.getRenderTransform(),
//                     viewProjection,
//                     Mth.lerp(partialTick, entity.xOld, entity.getX()),
//                     Mth.lerp(partialTick, entity.yOld, entity.getY()),
//                     Mth.lerp(partialTick, entity.zOld, entity.getZ()),
//                     camX,
//                     camY,
//                     camZ
//             );
//             ((ContraptionMatricesAccessor) matrices).invokeSetup(viewProjection, entity);
//             viewProjection.popPose();
//             ci.cancel();
//         }
//     }
// }
