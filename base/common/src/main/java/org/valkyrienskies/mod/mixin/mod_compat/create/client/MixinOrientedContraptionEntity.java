package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(OrientedContraptionEntity.class)
public class MixinOrientedContraptionEntity {

    @Shadow
    private Vec3 getCartOffset(float partialTicks, Entity ridingEntity) {
        return Vec3.ZERO;
    }

    @Redirect(method = "applyLocalTransforms", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/OrientedContraptionEntity;repositionOnCart(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/world/entity/Entity;)V"))
    private void redirectRepositionOnCart(OrientedContraptionEntity instance, PoseStack matrixStack, float partialTicks, Entity ridingEntity) {

        Vec3 cartPos = getCartOffset(partialTicks, ridingEntity);
        if (cartPos != Vec3.ZERO) matrixStack.translate(cartPos.x, cartPos.y, cartPos.z);

        ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(instance.getCommandSenderWorld(), ridingEntity.blockPosition());
        if (ship != null) {
            Quaterniond quaternion = new Quaterniond();
            ship.getRenderTransform().getShipToWorld().getNormalizedRotation(quaternion);
            TransformStack.cast(matrixStack).rotateCentered(VectorConversionsMCKt.toMinecraft(quaternion));
        }
    }

    @Redirect(method = "repositionOnContraption", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void redirectTranslate(final PoseStack instance, final double pose, final double d, final double e) {
        instance.translate(pose, d, e);

        ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(Minecraft.getInstance().level, ((AbstractContraptionEntity) (Object) this).getVehicle().blockPosition());
        if (ship != null) {
            Quaterniond quaternion = new Quaterniond();
            ship.getRenderTransform().getShipToWorld().getNormalizedRotation(quaternion);
            TransformStack.cast(instance).rotateCentered(VectorConversionsMCKt.toMinecraft(quaternion));
        }
    }

    @Redirect(method = "getContraptionOffset", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getPassengerPosition(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectGetPassengerPosition(AbstractContraptionEntity instance, Entity passenger, float partialTicks) {
        Vec3 result = instance.getPassengerPosition(passenger, partialTicks);

        ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(instance.getCommandSenderWorld(), instance.position());
        if (ship != null) {
            Vector3d dest = new Vector3d();
            ship.getRenderTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(result), dest);
            result = VectorConversionsMCKt.toMinecraft(dest);
        }
        return result;
    }
}
