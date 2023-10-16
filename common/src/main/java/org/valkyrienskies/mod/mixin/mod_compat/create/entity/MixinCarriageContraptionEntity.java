package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(CarriageContraptionEntity.class)
public abstract class MixinCarriageContraptionEntity {
    @Unique
    private Level world;

    @Inject(
            method = "control",
            at = @At("HEAD"), locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectCaptureLevel(
            final BlockPos controlsLocalPos, final Collection<Integer> heldControls, final Player player,
            final CallbackInfoReturnable<Boolean> cir) {
        this.world = player.level;
    }

    @Redirect(
            method = "control",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
            )
    )
    private boolean redirectCloserThan(final Vec3 instance, final Position arg, final double d) {
        Vec3 newVec3 = instance;
        if (VSGameUtilsKt.isBlockInShipyard(this.world, new BlockPos(instance.x, instance.y, instance.z))) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos(this.world, instance);
            newVec3 = VSGameUtilsKt.toWorldCoordinates(ship, instance);
        }
        return newVec3.closerThan(arg, d);
    }
}
