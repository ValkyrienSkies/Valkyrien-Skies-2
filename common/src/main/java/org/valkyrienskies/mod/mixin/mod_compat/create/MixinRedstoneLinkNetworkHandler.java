package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(RedstoneLinkNetworkHandler.class)
public abstract class MixinRedstoneLinkNetworkHandler {

    @Unique
    private static Level harvestedWorld;

    @Redirect(
            method = "withinRange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
            )
    )
    private static boolean redirectCloserThan(BlockPos instance, Vec3i vec3i, double v) {
        Ship ship1 = VSGameUtilsKt.getShipManagingPos(harvestedWorld, instance);
        Ship ship2 = VSGameUtilsKt.getShipManagingPos(harvestedWorld, new BlockPos(vec3i));
        Vec3 pos1 = Vec3.atLowerCornerOf(instance);
        Vec3 pos2 = Vec3.atLowerCornerOf(vec3i);
        if (ship1 != null) {
            pos1 = VectorConversionsMCKt.toMinecraft(ship1.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(pos1)));
        }
        if (ship2 != null) {
            pos2 = VectorConversionsMCKt.toMinecraft(ship2.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(pos2)));
        }
        return pos1.closerThan(pos2, v);
    }

    @Inject(method = "updateNetworkOf", at = @At("HEAD"), remap = false)
    private void harvestLevel(LevelAccessor world, IRedstoneLinkable actor, CallbackInfo ci) {
        harvestedWorld = (Level) world;
    }
}
