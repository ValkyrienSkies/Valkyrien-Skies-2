package org.valkyrienskies.mod.mixin.server.command.level;

import com.mojang.authlib.GameProfile;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends Player {

    public MixinServerPlayer(Level level, BlockPos blockPos, float f,
        GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Shadow
    public abstract void teleportTo(double d, double e, double f);

    @Shadow
    public abstract boolean teleportTo(ServerLevel serverLevel, double d, double e, double f, Set<RelativeMovement> set, float g, float h);

    @Inject(
        at = @At("HEAD"),
        method = "teleportTo(DDD)V",
        cancellable = true
    )
    private void beforeTeleportTo(final double x, final double y, final double z, final CallbackInfo ci) {
        ServerLevel level = ((ServerPlayer) (Object) this).serverLevel();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, x, y, z);
        if (ship != null) {
            ci.cancel();
            final Vector3d inWorld = VSGameUtilsKt.toWorldCoordinates(ship, x, y, z);
            this.teleportTo(inWorld.x, inWorld.y, inWorld.z);
        }
    }

    @Inject(
        at = @At("HEAD"),
        method = "dismountTo",
        cancellable = true
    )
    private void beforeDismountTo(final double x, final double y, final double z, final CallbackInfo ci) {
        ServerLevel level = ((ServerPlayer) (Object) this).serverLevel();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, x, y, z);
        if (ship != null) {
            ci.cancel();

            Vector3d lookVector = VectorConversionsMCKt.toJOML(this.getLookAngle());
            final Vector3d transformedLook = ship.getTransform().getShipToWorld().transformDirection(lookVector);
            final double yaw = Math.atan2(-transformedLook.x, transformedLook.z) * 180.0 / Math.PI;
            final double pitch = Math.atan2(-transformedLook.y, Math.sqrt((transformedLook.x * transformedLook.x) + (transformedLook.z * transformedLook.z))) * 180.0 / Math.PI;
            this.setYRot((float) yaw);
            this.setXRot((float) pitch);

            //Predict the position 2 ticks ahead for dismount
            final Vector3d inWorld = ship.getTransform().getShipToWorld().transformPosition(x, y, z, new Vector3d());
            final Vector3d inWorldPrev = ship.getPrevTickTransform().getShipToWorld().transformPosition(x, y, z, new Vector3d());
            final Vector3d inWorldNext = inWorld.mul(3, new Vector3d()).sub(inWorldPrev.mul(2, new Vector3d()));
            this.teleportTo(level, inWorldNext.x, inWorldNext.y, inWorldNext.z, Set.of(), this.getYRot(), this.getXRot());
            ((IEntityDraggingInformationProvider)this).vs$dragImmediately(ship);
        }
    }
}
