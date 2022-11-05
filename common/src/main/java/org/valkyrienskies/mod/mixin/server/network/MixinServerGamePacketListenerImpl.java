package org.valkyrienskies.mod.mixin.server.network;

import java.util.Collections;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @Shadow
    public ServerPlayer player;

    @Shadow
    public abstract void send(Packet<?> arg);

    @Shadow
    private int awaitingTeleport;

    @Shadow
    private int tickCount;

    @Shadow
    private Vec3 awaitingPositionFromClient;

    @Shadow
    private int awaitingTeleportTime;

    @Shadow
    @Final
    private MinecraftServer server;

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        ),
        method = "handleUseItemOn"
    )
    private Vec3 skipDistanceCheck2(final Vec3 instance, final Vec3 vec3) {
        return VSGameUtilsKt.toWorldCoordinates(player.level, instance.subtract(vec3));
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ChunkPos;getChessboardDistance(Lnet/minecraft/world/level/ChunkPos;)I"
        ),
        method = "handleUseItemOn"
    )
    private int skipDistanceCheck1(final ChunkPos instance, final ChunkPos chunkPos) {
        return 0;
    }

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Redirect(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"
        )
    )
    public double includeShipsInBlockInteractDistanceCheck(
        final ServerPlayer receiver, final double x, final double y, final double z) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
        } else {
            return 0;
        }
    }

    @Redirect(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"
        )
    )
    private boolean shouldSkipMoveCheck1(final ServerGamePacketListenerImpl instance) {
        return !VSGameConfig.SERVER.getEnableMovementChecks();
    }

    @Redirect(
        method = "handleMoveVehicle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"
        )
    )
    private boolean shouldSkipMoveCheck2(final ServerGamePacketListenerImpl instance) {
        return !VSGameConfig.SERVER.getEnableMovementChecks();
    }

    @Redirect(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;isCreative()Z"
        )
    )
    private boolean shouldSkipMoveCheck(final ServerPlayerGameMode instance) {
        return !VSGameConfig.SERVER.getEnableMovementChecks();
    }

    // Fixes:
    // https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/87
    // Bed Bug
    @Inject(
        method = "teleport(DDDFFLjava/util/Set;)V",
        at = @At(value = "HEAD"),
        cancellable = true
    )
    private void transformTeleport(final double x, final double y, final double z, final float yaw, final float pitch,
        final Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeSet, final CallbackInfo ci) {

        if (!VSGameConfig.SERVER.getTransformTeleports()) {
            return;
        }

        final BlockPos blockPos = new BlockPos(x, y, z);
        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) player.level, blockPos);

        // TODO add flag to disable this https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/30
        if (ship != null) {
            final Vector3d pos = new Vector3d(x, y, z);
            ship.getShipToWorld().transformPosition(pos);

            this.awaitingPositionFromClient = VectorConversionsMCKt.toMinecraft(pos);
            if (++this.awaitingTeleport == Integer.MAX_VALUE) {
                this.awaitingTeleport = 0;
            }
            this.awaitingTeleportTime = this.tickCount;
            this.player.absMoveTo(pos.x, pos.y, pos.z, yaw, pitch);

            this.send(
                new ClientboundPlayerPositionPacket(pos.x, pos.y, pos.z, yaw, pitch, Collections.emptySet(),
                    awaitingTeleport, false));
            ci.cancel();
        }
    }

    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    void onDisconnect(final Component reason, final CallbackInfo ci) {
        VSGameUtilsKt.getShipObjectWorld(this.server).onDisconnect(
            VSGameUtilsKt.getPlayerWrapper(this.player)
        );
    }

}
