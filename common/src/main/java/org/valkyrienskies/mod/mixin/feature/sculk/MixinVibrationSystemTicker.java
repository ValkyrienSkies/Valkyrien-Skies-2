package org.valkyrienskies.mod.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem.Ticker;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem.User;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// This mixin injects into private methods of an interface, which is supported by up-to-date
// Fabric mixin, or with MixinBooster. Otherwise this will not work (but will not crash) as of Forge 1.20.1.
@Mixin(Ticker.class)
public interface MixinVibrationSystemTicker {
    @WrapOperation(method = "receiveVibration", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationInfo;pos()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 destWorldPos(VibrationInfo instance, Operation<Vec3> original,
        @Local(argsOnly = true) ServerLevel serverLevel) {
        return VSGameUtilsKt.toWorldCoordinates(serverLevel, original.call(instance));
    }

    @WrapOperation(method = "receiveVibration", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;getPositionSource()Lnet/minecraft/world/level/gameevent/PositionSource;"))
    private static PositionSource destSourcePos(User instance, Operation<PositionSource> original, @Local(argsOnly = true) ServerLevel serverLevel) {
        PositionSource trueResult = original.call(instance);
        Optional<Vec3> optPos = trueResult.getPosition(serverLevel);
        if (optPos.isPresent()) {
            /* Curiously, Minecraft doesn't have a decent PositionSource for
            Vec3 coordinates. While using BlockPos will cause precision loss
            up to half a block for each coordinate, it is far safer than
            implementing a custom PositionSource just for this specific case. */
            return new BlockPositionSource(
                BlockPos.containing(
                    VSGameUtilsKt.toWorldCoordinates(serverLevel, optPos.get())
                )
            );
        } else {
            // Let it go wrong the exact way it should without this mixin.
            return trueResult;
        }
    }

    @WrapOperation(method = "tryReloadVibrationParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;getPositionSource()Lnet/minecraft/world/level/gameevent/PositionSource;"))
    private static PositionSource destReloadSourcePos(User instance, Operation<PositionSource> original, @Local(argsOnly = true) ServerLevel serverLevel) {
        return destSourcePos(instance, original, serverLevel);
    }
}
