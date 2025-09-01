package org.valkyrienskies.mod.mixin.mod_compat.common_create.client;

import com.simibubi.create.foundation.sound.SoundScapes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SoundScapes.class)
public abstract class MixinSoundScapes {

    @Redirect(method = "outOfRange", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
    private static boolean redirectCloserThan(BlockPos cameraPos, Vec3i pos, double v) {
        Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(
            Minecraft.getInstance().player.level(), (BlockPos) pos
        );
        return new Vec3(cameraPos.getX(), cameraPos.getY(), cameraPos.getZ()).closerThan(worldPos, v);
    }

    @ModifyVariable(method = "play", at = @At("HEAD"), index = 1, argsOnly = true, remap = false)
    private static BlockPos modBlockPos(BlockPos pos) {
        return BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(
            Minecraft.getInstance().player.level(), pos
        ));
    }
}
