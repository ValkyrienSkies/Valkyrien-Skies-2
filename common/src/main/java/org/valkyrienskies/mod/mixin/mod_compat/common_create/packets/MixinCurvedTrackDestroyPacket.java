package org.valkyrienskies.mod.mixin.mod_compat.common_create.packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.track.CurvedTrackDestroyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(CurvedTrackDestroyPacket.class)
public abstract class MixinCurvedTrackDestroyPacket {
    @WrapOperation(
            method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/trains/track/TrackBlockEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
            )
    )
    private boolean wrapCloserThan(
        final BlockPos instance, final Vec3i vec3i, final double v, final Operation<Boolean> original,
        @Local(argsOnly = true) final ServerPlayer player
    ) {
        BlockPos blockPos = BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(player.level(), instance));
        return original.call(blockPos, vec3i, v);
    }
}
