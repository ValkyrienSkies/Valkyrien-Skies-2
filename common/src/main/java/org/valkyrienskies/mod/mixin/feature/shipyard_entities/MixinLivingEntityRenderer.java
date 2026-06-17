package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Because {@link MixinEntityRenderDispatcher} transforms the Entity according to Ship Transform,
 *  living Entities are rendered to be looking at different direction than they are supposed to.
 *  to correct that, this mixin will modify the rendered rotation of the living entities
 *  by recalculating the angles from the look vector.
 * @author Bunting_chj
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD")
    )
    private void calcBodyRotation(LivingEntity livingEntity, float f, float g, PoseStack poseStack,
        MultiBufferSource multiBufferSource, int i, CallbackInfo ci, @Share("eulerDegrees")
        LocalRef<Vector3f> eulerDegrees, @Share("eulerDegreesOld")
        LocalRef<Vector3f> eulerDegreesOld) {
        if (VSGameUtilsKt.getShipMountedTo(livingEntity) instanceof ClientShip ship) {
            Quaternionf rotation = new Quaternionf(ship.getRenderTransform().getRotation()).invert();
            Vector3f lookVector = livingEntity.getViewVector(1.0f).toVector3f();
            Vector3f lookVectorOld = livingEntity.getViewVector(0.0f).toVector3f();
            lookVector.rotate(rotation);
            lookVectorOld.rotate(rotation);
            Vector3f zero = new Vector3f(0, 0, 1);
            eulerDegrees.set(zero.rotationTo(lookVector, new Quaternionf()).getEulerAnglesYXZ(new Vector3f()).mul(Mth.RAD_TO_DEG));
            eulerDegreesOld.set(zero.rotationTo(lookVectorOld, new Quaternionf()).getEulerAnglesYXZ(new Vector3f()).mul(Mth.RAD_TO_DEG));
        }
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;yHeadRot:F", opcode = Opcodes.GETFIELD)
    )
    private float modifyYHeadRot(LivingEntity livingEntity, Operation<Float> original, @Share("eulerDegrees")
    LocalRef<Vector3f> eulerDegrees){
        if(eulerDegrees.get() != null) {
            return -eulerDegrees.get().y;
        }
        else return original.call(livingEntity);
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;yHeadRotO:F", opcode = Opcodes.GETFIELD)
    )
    private float modifyYHeadRotOld(LivingEntity livingEntity, Operation<Float> original, @Share("eulerDegreesOld")
    LocalRef<Vector3f> eulerDegreesOld){
        if(eulerDegreesOld.get() != null) {
            return -eulerDegreesOld.get().y;
        }
        else return original.call(livingEntity);
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;yBodyRot:F", opcode = Opcodes.GETFIELD)
    )
    private float modifyYBodyRot(LivingEntity livingEntity, Operation<Float> original, @Share("eulerDegrees")
    LocalRef<Vector3f> eulerDegrees){
        if(eulerDegrees.get() != null) {
            return original.call(livingEntity) - livingEntity.yHeadRot - eulerDegrees.get().y;
        }
        else return original.call(livingEntity);
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;yBodyRotO:F", opcode = Opcodes.GETFIELD)
    )
    private float modifyYBodyRotOld(LivingEntity livingEntity, Operation<Float> original, @Share("eulerDegreesOld")
    LocalRef<Vector3f> eulerDegreesOld){
        if(eulerDegreesOld.get() != null) {
            return original.call(livingEntity) - livingEntity.yHeadRotO - eulerDegreesOld.get().y;
        }
        else return original.call(livingEntity);
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F")
    )
    private float modifyXRot(LivingEntity livingEntity, Operation<Float> original, @Share("eulerDegrees")
    LocalRef<Vector3f> eulerDegrees){
        if(eulerDegrees.get() != null) {
            return eulerDegrees.get().x;
        }
        else return original.call(livingEntity);
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;xRotO:F", opcode = Opcodes.GETFIELD)
    )
    private float modifyXRotOld(LivingEntity livingEntity, Operation<Float> original, @Share("eulerDegreesOld")
    LocalRef<Vector3f> eulerDegreesOld){
        if(eulerDegreesOld.get() != null) {
            return eulerDegreesOld.get().x;
        }
        else return original.call(livingEntity);
    }
}
