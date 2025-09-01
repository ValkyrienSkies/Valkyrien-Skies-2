 package org.valkyrienskies.mod.mixin.mod_compat.old_create.client;

 import net.minecraft.util.Mth;
 import net.minecraft.world.entity.Entity;
 import net.minecraft.world.phys.AABB;
 import org.jetbrains.annotations.NotNull;
 import org.joml.Matrix4f;
 import org.spongepowered.asm.mixin.Mixin;
 import org.spongepowered.asm.mixin.Pseudo;
 import org.spongepowered.asm.mixin.injection.At;
 import org.spongepowered.asm.mixin.injection.Coerce;
 import org.spongepowered.asm.mixin.injection.Inject;
 import org.spongepowered.asm.mixin.injection.Redirect;
 import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
 import org.valkyrienskies.core.api.ships.ClientShip;
 import org.valkyrienskies.mod.common.VSClientGameUtils;
 import org.valkyrienskies.mod.common.VSGameUtilsKt;
 import org.valkyrienskies.mod.mixin.mod_compat.old_create.accessors.ContraptionRenderInfoAccessor;

 @Mixin(targets = "com.simibubi.create.content.contraptions.render.FlwContraption")
 public class MixinFlwContraption {

     @Inject(at = @At("HEAD"), method = "setupModelViewPartial(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;DDDF)V", cancellable = true, remap = false)
     private static void beforeSetupModelViewPartial(Matrix4f matrix, Matrix4f modelMatrix,
         @Coerce Entity entity, double camX, double camY, double camZ, float pt, CallbackInfo ci) {

         if (VSGameUtilsKt.getShipManaging(entity) instanceof final ClientShip ship) {
             VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(),
                     matrix,
                     Mth.lerp(pt, entity.xOld, entity.getX()),
                     Mth.lerp(pt, entity.yOld, entity.getY()),
                     Mth.lerp(pt, entity.zOld, entity.getZ()),
                     camX,
                     camY,
                     camZ
             );

             matrix.mul(modelMatrix);
             ci.cancel();
         }
     }

     @Redirect(
             at = @At(
                     value = "INVOKE",
                     target = "Lnet/minecraft/world/phys/AABB;move(DDD)Lnet/minecraft/world/phys/AABB;"
             ),
             method = "beginFrame"
     )
     private @NotNull AABB transformLightboxToWorld(
         final AABB aabb, final double negCamX, final double negCamY, final double negCamZ
     )  {
         return VSGameUtilsKt.transformAabbToWorld(
             ((ContraptionRenderInfoAccessor)this).getContraption().entity.level(),
             aabb
         ).move(negCamX, negCamY, negCamZ);
     }
 }
