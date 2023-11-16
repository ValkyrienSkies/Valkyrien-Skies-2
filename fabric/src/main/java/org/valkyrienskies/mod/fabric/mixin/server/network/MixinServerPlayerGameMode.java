package org.valkyrienskies.mod.fabric.mixin.server.network;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerGameMode {

    @Final
    @Shadow
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;

    /**
     * Includes ships in server-side distance check when player breaks a block.
     */
    @ModifyExpressionValue(
        method = "handleBlockBreakAction",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    )
    public double handleBlockBreakAction(final Vec3 instance, final Vec3 vec3) {
        final BlockPos pos = new BlockPos(vec3.subtract(0.5, 0.5, 0.5));
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            final Vector3d blockCenter = VectorConversionsMCKt.toJOMLD(pos).add(0.5, 0.5, 0.5);
            return VSGameUtilsKt.getWorldCoordinates(level, pos, blockCenter)
                .distanceSquared(player.getX(), player.getY() + 1.5, player.getZ());
        } else {
            return instance.distanceToSqr(vec3);
        }
    }
}
