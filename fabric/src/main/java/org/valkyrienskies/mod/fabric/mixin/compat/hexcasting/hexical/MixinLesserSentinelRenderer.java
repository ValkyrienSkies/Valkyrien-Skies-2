package org.valkyrienskies.mod.fabric.mixin.compat.hexcasting.hexical;
// fixme
//import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//import com.llamalad7.mixinextras.sugar.Local;
//import com.mojang.blaze3d.vertex.PoseStack;
//import miyucomics.hexical.features.lesser_sentinels.LesserSentinelRenderer;
//import net.minecraft.world.phys.Vec3;
//import org.joml.Vector3f;
//import org.joml.Vector3fc;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Pseudo;
//import org.spongepowered.asm.mixin.injection.At;
//import org.valkyrienskies.core.api.ships.ClientShip;
//import org.valkyrienskies.mod.common.VSClientGameUtils;
//import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
//
//@Pseudo
//@Mixin(LesserSentinelRenderer.class)
//public class MixinLesserSentinelRenderer {
//    @WrapOperation(method = "init$lambda$1", at= @At(value = "INVOKE",
//        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
//    private static void valkyrienskies$renderOnShip(PoseStack instance, double d, double e, double f, Operation<Void> original, @Local(name = "pos") Vec3 pos, @Local(name = "camPos") Vec3 camPos) {
//        ClientShip ship = VSClientGameUtils.getClientShip(pos.x, pos.y, pos.z);
//        if (ship != null) {
//            Vec3 distance = VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(pos))).subtract(camPos);
//            original.call(instance, distance.x, distance.y, distance.z);
//            Vector3fc scale = ship.getRenderTransform().getShipToWorldScaling().get(new Vector3f());
//            instance.scale(scale.x(), scale.y(), scale.z());
//        } else {
//            original.call(instance, d, e, f);
//        }
//    }
//}
