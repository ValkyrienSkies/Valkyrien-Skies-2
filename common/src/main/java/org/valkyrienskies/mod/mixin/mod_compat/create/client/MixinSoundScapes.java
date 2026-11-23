package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.foundation.sound.SoundScapes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SoundScapes.class)
public abstract class MixinSoundScapes {

    @ModifyVariable(method = "play", at = @At("HEAD"), index = 1, argsOnly = true)
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
