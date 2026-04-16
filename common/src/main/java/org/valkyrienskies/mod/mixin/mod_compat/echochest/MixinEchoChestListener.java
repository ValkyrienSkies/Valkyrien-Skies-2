package org.valkyrienskies.mod.mixin.mod_compat.echochest;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.Optional;

@Pseudo
@Mixin(targets = "fuzs.echochest.world.level.block.entity.EchoChestListener")
public abstract class MixinEchoChestListener implements GameEventListener {
    /* Because Echo Chest is not a compile-time dependency I do not have any access to EchoChestBlockEntity
    or even EchoChestListener itself as a usable data type, so I can't shadow its blockEntity. I also cannot use
    WrapOperation for handleGameEvent so ModifyArg(s) looks to be the only way to hijack PositionSource without using
    any fields of the very class we mixin into.
     */
    @WrapOperation(
        // As of 1.20.1, auto-remapping implementations of obfuscated methods does not work if we are dealing
        // with a @Pseudo mixin. The obfuscated name is not changed in 1.21+ but hopefully we won't have to
        // care about remapping anyway.
        method = {"handleGameEvent", "method_32947", "m_214068_"}, require = 1,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double replaceTravelTime(Vec3 instance, Vec3 dest, Operation<Double> original, @Local(argsOnly = true) ServerLevel level) {
        return original.call(instance, VSGameUtilsKt.toWorldCoordinates(level, dest));
    }

    @ModifyArg(
        method = {"handleGameEvent", "method_32947", "m_214068_"}, require = 1,
        at = @At(value = "INVOKE", target = "net/minecraft/core/particles/VibrationParticleOption.<init>(Lnet/minecraft/world/level/gameevent/PositionSource;I)V"
    ))
    public PositionSource replacePositionSource(PositionSource source, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) Vec3 pos) {
        if (level != null) {
            Optional<Vec3> optPos = source.getPosition(level);
            if (optPos.isPresent()) {
                Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(level, optPos.get());
                return new BlockPositionSource(BlockPos.containing(worldPos));
            }
        }
        return source;
    }
}
