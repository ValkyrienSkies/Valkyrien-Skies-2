package org.valkyrienskies.mod.mixin.mod_compat.echochest;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.Optional;

@Pseudo
@Mixin(targets = { "fuzs.echochest.world.level.block.entity.EchoChestListener" }, remap = false)
public abstract class MixinEchoChestListener {
    /* Because Echo Chest is not a compile-time dependency I do not have any access to EchoChestBlockEntity
    or even EchoChestListener itself as a usable data type, so I can't shadow its blockEntity. I also cannot use
    WrapOperation for handleGameEvent so ModifyArg(s) looks to be the only way to hijack PositionSource without using
    any fields of the very class we mixin into.
     */
    @ModifyArgs(method = "handleGameEvent", at = @At(
        value = "INVOKE",
        target = "net/minecraft/core/particles/VibrationParticleOption.<init>(Lnet/minecraft/world/level/gameevent/PositionSource;I)V"
    ))
    public void destSourcePos(Args args, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) Vec3 pos) {
        PositionSource trueSource = args.get(0);
        if (level != null) {
            Optional<Vec3> optPos = trueSource.getPosition(level);
            if (optPos.isPresent()) {
                Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(level, optPos.get());
                args.set(0, new BlockPositionSource(BlockPos.containing(worldPos)));
                args.set(1, Mth.floor(pos.distanceTo(worldPos)));
            }
        }
    }
}
