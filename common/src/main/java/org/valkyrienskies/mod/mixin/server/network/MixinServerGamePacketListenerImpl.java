package org.valkyrienskies.mod.mixin.server.network;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.mixinducks.server.ShipMovementListener;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl implements ShipMovementListener {
    @Shadow private double firstGoodX;
    @Shadow private double firstGoodY;
    @Shadow private double firstGoodZ;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodY;
    @Shadow private double lastGoodZ;
    @Shadow private Entity lastVehicle;
    @Shadow private double vehicleFirstGoodX;
    @Shadow private double vehicleFirstGoodY;
    @Shadow private double vehicleFirstGoodZ;
    @Shadow private double vehicleLastGoodX;
    @Shadow private double vehicleLastGoodY;
    @Shadow private double vehicleLastGoodZ;

    @Override
    public void vs$onShipDrag(Entity entity, Vector3dc movement) {
        if (entity == player) {
            firstGoodX += movement.x();
            firstGoodY += movement.y();
            firstGoodZ += movement.z();
            lastGoodX += movement.x();
            lastGoodY += movement.y();
            lastGoodZ += movement.z();
        }
        if (entity == lastVehicle) {
            vehicleFirstGoodX += movement.x();
            vehicleFirstGoodY += movement.y();
            vehicleFirstGoodZ += movement.z();
            vehicleLastGoodX += movement.x();
            vehicleLastGoodY += movement.y();
            vehicleLastGoodZ += movement.z();
        }
    }

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

    @ModifyExpressionValue(
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;aboveGroundTickCount:I", ordinal = 0),
        method = "tick"
    )
    private int noFlyKick(final int original) {
        if (VSGameConfig.SERVER.getEnableMovementChecks()) {
            return original;
        } else {
            return 0;
        }
    }

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        ),
        method = "handleUseItemOn"
    )
    private Vec3 skipDistanceCheck2(final Vec3 instance, final Vec3 vec3, final Operation<Vec3> subtract) {
        return VSGameUtilsKt.toWorldCoordinates(player.level(), subtract.call(instance, vec3));
    }

    /*
    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ChunkPos;getChessboardDistance(Lnet/minecraft/world/level/ChunkPos;)I"
        ),
        method = "handleUseItemOn"
    )
    private int skipDistanceCheck(final ChunkPos instance, final ChunkPos chunkPos, final Operation<Integer> getChessboardDistance) {
        return 0;
    }

     */

    @WrapOperation(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"
        ),
        require = 0
    )
    private boolean shouldSkipMoveCheck1(final ServerGamePacketListenerImpl instance,
        final Operation<Boolean> isSinglePlayerOwner) {
        return !VSGameConfig.SERVER.getEnableMovementChecks();
    }

    @WrapOperation(
        method = "handleMoveVehicle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"
        ),
        require = 0
    )
    private boolean shouldSkipMoveCheck2(final ServerGamePacketListenerImpl instance,
        final Operation<Boolean> isSinglePlayerOwner) {
        return !VSGameConfig.SERVER.getEnableMovementChecks();
    }

    @WrapOperation(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;isCreative()Z"
        ),
        require = 0
    )
    private boolean shouldSkipMoveCheck(final ServerPlayerGameMode instance,
        final Operation<Boolean> isSinglePlayerOwner) {
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
        final Set<RelativeMovement> relativeSet, final CallbackInfo ci) {

        if (!VSGameConfig.SERVER.getTransformTeleports()) {
            return;
        }

        final BlockPos blockPos = BlockPos.containing(x, y, z);
        final ServerShip ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) player.level(), blockPos);

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
                    awaitingTeleport));
            ci.cancel();
        }
    }

    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    void onDisconnect(final Component reason, final CallbackInfo ci) {
        final VsiServerShipWorld world = VSGameUtilsKt.getShipObjectWorld(this.server);
        if (world != null) {
            world.onDisconnect(VSGameUtilsKt.getPlayerWrapper(this.player));
        }
    }

    // Drop shapes whose bounds don't intersect the query AABB before they reach
    // Shapes.joinIsNotEmpty — its discretized IndexMerger loses precision at the ~10⁷
    // coords of shipyard-frame entity shapes and reports a false intersect with the
    // player's world-frame bbox, locking the player in a teleport-back loop.
    @WrapOperation(
        method = "isPlayerCollidingWithAnythingNew",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;")
    )
    private Iterable<VoxelShape> vs$dropFarShapesFromMoveCheck(
        final LevelReader level, final Entity excluder, final AABB aabb,
        final Operation<Iterable<VoxelShape>> original
    ) {
        final Iterable<VoxelShape> raw = original.call(level, excluder, aabb);
        return () -> new Iterator<>() {
            final Iterator<VoxelShape> backing = raw.iterator();
            VoxelShape next = advance();

            private VoxelShape advance() {
                while (backing.hasNext()) {
                    final VoxelShape s = backing.next();
                    if (s.isEmpty()) continue;
                    if (s.bounds().intersects(aabb)) return s;
                }
                return null;
            }

            @Override public boolean hasNext() { return next != null; }
            @Override public VoxelShape next() {
                final VoxelShape r = next;
                next = advance();
                return r;
            }
        };
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER))
    private void vs$receiveWorldMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        final EntityDraggingInformation info = ((IEntityDraggingInformationProvider) player).getDraggingInformation();
        if (!info.getHandlingClientMovement()) info.setLastShipStoodOn(null);
    }

    @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER))
    private void vs$receiveWorldVehicleMovement(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        final Entity vehicle = player.getRootVehicle();
        if (vehicle != player && vehicle.getControllingPassenger() == player) {
            final EntityDraggingInformation info = ((IEntityDraggingInformationProvider) vehicle).getDraggingInformation();
            if (!info.getHandlingClientMovement()) info.setLastShipStoodOn(null);
        }
    }

}
