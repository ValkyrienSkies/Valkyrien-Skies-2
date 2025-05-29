package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.utils.CBCUtils;

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

    @Inject(
        method = "playBlastLikeSoundOnServer",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void mixinBlastLocation(ServerLevel level, double x, double y, double z, SoundEvent soundEvent,
        SoundSource soundSource, float volume, float pitch, float airAbsorption, CallbackInfo ci) {
        if (VSGameUtilsKt.isBlockInShipyard(level, x, y, z)) {
            Vector3d world = VSGameUtilsKt.toWorldCoordinates(level, x, y, z);
            CBCUtils.playBlastLikeSoundOnServer(level, world.x, world.y, world.z, soundEvent, soundSource, volume, pitch, airAbsorption);
            ci.cancel();
        }
    }
}
