package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.PacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.network.ServerboundSetFixedCannonMountValuePacket;

@Mixin(ServerboundSetFixedCannonMountValuePacket.class)
public abstract class MixinSSFCMVP {
    @Unique
    private Level level;

    @Inject(
        method = "handle",
        at = @At("HEAD")
    )
    private void stealLevel(Executor exec, PacketListener listener, ServerPlayer sender, CallbackInfo ci) {
        level = sender.level();
    }

    @Redirect(
        method = "*",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z")
    )
    private boolean mixinCloserThan(final BlockPos instance, final Vec3i arg, final double d) {
        if (level != null) {
            BlockPos newInstance = instance;
            final Ship ship = VSGameUtilsKt.getShipManagingPos(level, instance);
            if (ship != null) {
                newInstance = BlockPos.containing(toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, instance)));
            }
            return newInstance.closerThan(arg, d);
        }
        return instance.closerThan(arg, d);
    }
}

