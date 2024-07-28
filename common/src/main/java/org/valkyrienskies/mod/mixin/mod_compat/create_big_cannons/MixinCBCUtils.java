package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.utils.CBCUtils")
public abstract class MixinCBCUtils {
    @Inject(
        method = "getSurfaceNormalVector(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void getSurfaceNormalVector(Level level, BlockPos hitPos, Vec3 normal,
        CallbackInfoReturnable<Vec3> cir) {
        final Ship s = VSGameUtilsKt.getShipManagingPos(level, hitPos);
        if (s != null) {
            cir.setReturnValue(toMinecraft(s.getShipToWorld().transformDirection(toJOML(normal))));
        }
    }

    @WrapOperation(
        method = "playBlastLikeSoundOnServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"
        )
    )
    private static double mixinBlastLocation(ServerPlayer serverPlayer, double x, double y, double z, Operation<Double> original) {
        if (VSGameUtilsKt.isBlockInShipyard(serverPlayer.level(), x, y, z)) {
            Vector3d world = VSGameUtilsKt.toWorldCoordinates(serverPlayer.level(), x, y, z);
            x = world.x;
            y = world.y;
            z = world.z;
        }
        return original.call(serverPlayer, x,y,z);
    }
}
