package org.valkyrienskies.mod.forge.mixin.compat.create.client;

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
    private static boolean redirectCloserThan(BlockPos instance, Vec3i vec3i, double v) {
        Vec3 newVec3 = new Vec3(vec3i.getX(), vec3i.getY(), vec3i.getZ());
        Level world = Minecraft.getInstance().player.level();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(world, newVec3);
        if (ship != null) {
            newVec3 = VSGameUtilsKt.toWorldCoordinates(ship, newVec3);
        }
        return new Vec3(instance.getX(), instance.getY(), instance.getZ()).closerThan(newVec3, v);
    }

    @ModifyVariable(method = "play", at = @At("HEAD"), index = 1, argsOnly = true, remap = false)
    private static BlockPos modBlockPos(BlockPos value) {
        BlockPos result = value;
        Level world = Minecraft.getInstance().player.level();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(world, value);
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformPosition(value.getX(), value.getY(), value.getZ(), tempVec);
            result = BlockPos.containing(tempVec.x, tempVec.y, tempVec.z);
        }
        return result;
    }
}
