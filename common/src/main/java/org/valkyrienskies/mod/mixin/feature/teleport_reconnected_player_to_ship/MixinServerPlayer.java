package org.valkyrienskies.mod.mixin.feature.teleport_reconnected_player_to_ship;

import com.mojang.authlib.GameProfile;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends Player {

    @Shadow
    public abstract ServerLevel serverLevel();

    public MixinServerPlayer(final Level level, final BlockPos blockPos, final float f,
        final GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
        throw new IllegalStateException("Unreachable");
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    void teleportToShip(final CompoundTag compoundTag, final CallbackInfo ci) {
        if (!VSGameConfig.SERVER.getTeleportReconnectedPlayers())
            return;

        if (!compoundTag.contains("LastShipId"))
            return; // Player did not disconnect off of any ship

        final long lastShipId = compoundTag.getLong("LastShipId");

        final Ship ship = VSGameUtilsKt.getShipObjectWorld(serverLevel()).getAllShips().getById(lastShipId);
        // Don't teleport if the ship doesn't exist anymore
        if (ship == null)
            return;

        // Translate ship coords to world coords
        final double x = compoundTag.getDouble("RelativeShipX");
        final double y = compoundTag.getDouble("RelativeShipY");
        final double z = compoundTag.getDouble("RelativeShipZ");

        final Vector3d playerShipPosition = new Vector3d(x, y, z);
        final Vector3d playerWorldPosition = ship.getShipToWorld().transformPosition(playerShipPosition);

        setPos(playerWorldPosition.x, playerWorldPosition.y, playerWorldPosition.z);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    void rememberLastShip(final CompoundTag compoundTag, final CallbackInfo ci) {
        final EntityDraggingInformation draggingInformation = ((IEntityDraggingInformationProvider) this).getDraggingInformation();

        if (draggingInformation.getTicksSinceStoodOnShip() > VSGameConfig.SERVER.getMaxAirborneTicksForReconnectedPlayerTeleport())
            return;

        @Nullable final Long lastShipId = draggingInformation.getLastShipStoodOn();
        if (lastShipId == null)
            return;

        final Ship ship = VSGameUtilsKt.getShipObjectWorld(serverLevel()).getAllShips().getById(lastShipId);
        if (ship == null)
            return;

        compoundTag.putLong("LastShipId", lastShipId);

        // Get position relative to ship
        // (Technically, this grabs the position in the shipyard, but it works well enough...)
        final Vector3d playerWorldPosition = new Vector3d(getX(), getY(), getZ());
        final Vector3d playerShipPosition = ship.getWorldToShip().transformPosition(playerWorldPosition);

        compoundTag.putDouble("RelativeShipX", playerShipPosition.x);
        compoundTag.putDouble("RelativeShipY", playerShipPosition.y);
        compoundTag.putDouble("RelativeShipZ", playerShipPosition.z);
    }
}
