package org.valkyrienskies.mod.mixin.feature.entity_movement_packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.networking.PacketEntityShipMotion;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor;
import org.valkyrienskies.mod.mixin.accessors.server.level.TrackedEntityAccessor;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(ServerEntity.class)
public class MixinServerEntity {
    @Shadow @Final private Entity entity;
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private VecDeltaCodec positionCodec;
    @Shadow @Final private int updateInterval;
    @Shadow private int tickCount;
    @Unique private Long vs$lastShipId;
    @Unique private boolean vs$sentShipMotion;

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void vs$sendAttachmentChange(CallbackInfo ci) {
        vs$sentShipMotion = false;
        final ServerShip ship = vs$currentShip();
        final Long shipId = ship == null ? null : ship.getId();
        if (!Objects.equals(vs$lastShipId, shipId) || ship != null && tickCount % updateInterval == 0) {
            vs$broadcast(vs$motion(ship));
            vs$sentShipMotion = true;
            if (ship == null) positionCodec.setBase(entity.trackingPosition());
            vs$lastShipId = shipId;
        }
    }

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void vs$sendInitialAttachment(ServerPlayer player, CallbackInfo ci) {
        final ServerShip ship = vs$currentShip();
        if (ship != null) vs$send(vs$motion(ship), player);
    }

    @WrapOperation(method = "sendChanges", at = @At(value = "INVOKE",
        target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void wrapBroadcastAccept(Consumer instance, Object packet, Operation<Void> original) {
        if (packet instanceof ClientboundSetEntityMotionPacket || packet instanceof ClientboundTeleportEntityPacket
            || packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundRotateHeadPacket) {
            final ServerShip ship = vs$currentShip();
            if (ship != null) {
                if (!vs$sentShipMotion) {
                    vs$broadcast(vs$motion(ship));
                    vs$sentShipMotion = true;
                }
                return;
            }
        }
        original.call(instance, packet);
    }

    @Unique
    private ServerShip vs$currentShip() {
        if (!EntityDragger.isDraggable(entity)) return null;
        final ShipMountedToData mounted = VSGameUtilsKt.getShipMountedToData(entity, null);
        if (mounted != null && mounted.getShipMountedTo() instanceof ServerShip ship) return ship;
        final EntityDraggingInformation info = ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
        if (!info.isEntityBeingDraggedByAShip() || info.getLastShipStoodOn() == null) return null;
        return VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(info.getLastShipStoodOn());
    }

    @Unique
    private PacketEntityShipMotion vs$motion(ServerShip ship) {
        Vector3dc position = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        final Vector3d velocity = new Vector3d(entity.getDeltaMovement().x, entity.getDeltaMovement().y,
            entity.getDeltaMovement().z);
        double yaw = entity.getYRot();
        double headYaw = entity.getYHeadRot();
        int age = 0;
        if (ship != null) {
            final ShipMountedToData mounted = VSGameUtilsKt.getShipMountedToData(entity, null);
            if (mounted != null && mounted.getShipMountedTo().getId() == ship.getId()) {
                position = mounted.getMountPosInShip();
            } else {
                position = ship.getWorldToShip().transformPosition(position, new Vector3d());
                age = ((IEntityDraggingInformationProvider) entity).getDraggingInformation().getTicksSinceStoodOnShip();
            }
            ship.getWorldToShip().transformDirection(velocity);
            yaw = EntityLerper.INSTANCE.yawToShip(ship, yaw);
            headYaw = EntityLerper.INSTANCE.yawToShip(ship, headYaw);
        }
        return new PacketEntityShipMotion(entity.getId(), ship == null ? -1L : ship.getId(),
            position.x(), position.y(), position.z(), velocity.x, velocity.y, velocity.z(),
            yaw, entity.getXRot(), entity.onGround(), entity.getX(), entity.getY(), entity.getZ(), headYaw, age);
    }

    @Unique
    private void vs$broadcast(PacketEntityShipMotion packet) {
        final Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).getEntityMap().get(entity.getId());
        if (tracked instanceof TrackedEntityAccessor accessor) {
            for (ServerPlayerConnection connection : accessor.getSeenBy()) vs$send(packet, connection.getPlayer());
        }
    }

    @Unique
    private void vs$send(PacketEntityShipMotion packet, ServerPlayer player) {
        VSGameUtilsKt.getVsCore().getSimplePacketNetworking().sendToClients(packet,
            ((PlayerDuck) player).vs_getPlayer());
    }
}
