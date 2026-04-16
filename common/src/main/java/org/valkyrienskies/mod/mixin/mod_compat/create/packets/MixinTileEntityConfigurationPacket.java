package org.valkyrienskies.mod.mixin.mod_compat.create.packets;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(BlockEntityConfigurationPacket.class)
public abstract class MixinTileEntityConfigurationPacket {
    @Unique
    private Level _clockworkLevel;

    @Redirect(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
            )
    )
    private boolean redirectCloserThan(final BlockPos instance, final Vec3i vec3i, final double v, @Local ServerPlayer player) {
        BlockPos blockPos = instance;
        if (VSGameUtilsKt.isBlockInShipyard(this._clockworkLevel, instance)) {
            double distance = VSGameUtilsKt.squaredDistanceToInclShips(player, instance.getX(), instance.getY(), instance.getZ());
            return distance < Mth.square(v);
        }
        return blockPos.closerThan(vec3i, v);
    }

    @Redirect(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean injectCaptureLevel(final Level instance, final BlockPos pos) {
        this._clockworkLevel = instance;
        return instance.isLoaded(pos);
    }
}
