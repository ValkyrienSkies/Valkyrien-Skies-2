package org.valkyrienskies.mod.mixin.feature.entity_movement_packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.impl.networking.simple.SimplePacket;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.networking.PacketEntityShipMotion;
import org.valkyrienskies.mod.common.networking.PacketMobShipRotation;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor;
import org.valkyrienskies.mod.mixin.accessors.server.level.TrackedEntityAccessor;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(ServerEntity.class)
public class MixinServerEntity {

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private ServerLevel level;

    @Unique
    private Long vs$lastSentShipId = null;

    @Unique
    private int vs$lastSentMotionTick = Integer.MIN_VALUE;

    @Unique
    private boolean vs$sentPacketThisTick = false;

    @Unique
    private void vs$sendToTrackedViewers(final SimplePacket packet) {
        final ChunkMap.TrackedEntity trackedEntity =
            ((ChunkMapAccessor) level.getChunkSource().chunkMap).getEntityMap().get(entity.getId());
        if (trackedEntity == null) {
            return;
        }

        for (final ServerPlayerConnection connection : ((TrackedEntityAccessor) trackedEntity).getSeenBy()) {
            ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking()
                .sendToClients(packet, ((PlayerDuck) connection.getPlayer()).vs_getPlayer());
        }
    }

    @Unique
    private int vs$getServerTick() {
        return level.getServer() != null ? level.getServer().getTickCount() : 0;
    }

    @Unique
    private int vs$getAdaptiveSendInterval(final double relativeSpeed) {
        if (relativeSpeed > 0.20) {
            return 1;
        }
        if (relativeSpeed > 0.06) {
            return 2;
        }
        if (relativeSpeed > 0.015) {
            return 3;
        }
        return 5;
    }

    @Unique
    private void vs$sendShipMotionPacket(final ServerShip ship, final EntityDraggingInformation dragInfo) {
        Vector3d position = ship.getWorldToShip().transformPosition(new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
        if (dragInfo.getServerRelativePlayerPosition() != null) {
            position = new Vector3d(dragInfo.getServerRelativePlayerPosition());
        }
        final Vector3d motion = ship.getTransform().getWorldToShip().transformDirection(
            new Vector3d(entity.getDeltaMovement().x(), entity.getDeltaMovement().y(), entity.getDeltaMovement().z()),
            new Vector3d()
        );
        final double yaw = EntityLerper.INSTANCE.yawToShip(ship, entity.getYRot());
        final double pitch = entity.getXRot();

        vs$sendToTrackedViewers(new PacketEntityShipMotion(
            entity.getId(),
            ship.getId(),
            position.x,
            position.y,
            position.z,
            motion.x,
            motion.y,
            motion.z,
            yaw,
            pitch
        ));

        vs$lastSentShipId = ship.getId();
        vs$lastSentMotionTick = vs$getServerTick();
        vs$sentPacketThisTick = true;
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void vs$onSendChangesHead(final CallbackInfo ci) {
        vs$sentPacketThisTick = false;
    }

    @Inject(method = "sendChanges", at = @At("TAIL"))
    private void vs$onSendChangesTail(final CallbackInfo ci) {
        if (!EntityDragger.isDraggable(entity)) {
            return;
        }

        final IEntityDraggingInformationProvider draggedEntity = (IEntityDraggingInformationProvider) entity;
        final EntityDraggingInformation dragInfo = draggedEntity.getDraggingInformation();
        final int serverTick = vs$getServerTick();

        if (dragInfo != null && dragInfo.isEntityBeingDraggedByAShip() && dragInfo.getLastShipStoodOn() != null) {
            final ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(dragInfo.getLastShipStoodOn());
            if (ship == null) {
                return;
            }

            if (entity instanceof LivingEntity || vs$sentPacketThisTick) {
                return;
            }

            final Vector3d relativeMotion = ship.getTransform().getWorldToShip().transformDirection(
                new Vector3d(entity.getDeltaMovement().x(), entity.getDeltaMovement().y(), entity.getDeltaMovement().z()),
                new Vector3d()
            );
            final int interval = vs$getAdaptiveSendInterval(relativeMotion.length());
            if (serverTick - vs$lastSentMotionTick < interval) {
                return;
            }

            vs$sendShipMotionPacket(ship, dragInfo);
            return;
        }

        if (!vs$sentPacketThisTick && vs$lastSentShipId != null) {
            vs$sendToTrackedViewers(
                new PacketEntityShipMotion(entity.getId(), -1L, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            );
            vs$lastSentShipId = null;
            vs$lastSentMotionTick = serverTick;
            vs$sentPacketThisTick = true;
        }
    }

    /**
     * @author Tomato
     * @reason Intercept entity motion packets to send our own data, then cancel the original packet.
     */
    @WrapOperation(
        method = "sendChanges",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V")
    )
    private void wrapBroadcastAccept(Consumer instance, Object t, Operation<Void> original) {
        if (t instanceof ClientboundSetEntityMotionPacket || t instanceof ClientboundTeleportEntityPacket || t instanceof ClientboundMoveEntityPacket || t instanceof ClientboundRotateHeadPacket || t instanceof ClientboundSetEntityDataPacket) {
            if (EntityDragger.isDraggable(entity)) {
                IEntityDraggingInformationProvider draggedEntity = (IEntityDraggingInformationProvider) entity;
                EntityDraggingInformation dragInfo = draggedEntity.getDraggingInformation();

                if (dragInfo != null && dragInfo.isEntityBeingDraggedByAShip() && dragInfo.getLastShipStoodOn() != null) {
                    ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(dragInfo.getLastShipStoodOn());
                    if (ship != null) {

                        Vector3d position = ship.getWorldToShip().transformPosition(new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
                        if (dragInfo.getServerRelativePlayerPosition() != null) {
                            position = new Vector3d(dragInfo.getServerRelativePlayerPosition());
                        }
                        Vector3d motion = ship.getTransform().getWorldToShip().transformDirection(new Vector3d(entity.getDeltaMovement().x(), entity.getDeltaMovement().y(), entity.getDeltaMovement().z()), new Vector3d());
                        double yaw;
                        if (!(t instanceof ClientboundRotateHeadPacket)) {
                            yaw = EntityLerper.INSTANCE.yawToShip(ship, entity.getYRot());
                        } else {
                            yaw = EntityLerper.INSTANCE.yawToShip(ship, entity.getYHeadRot());
                        }
                        double pitch = entity.getXRot();
                        SimplePacket vsPacket;
                        if (!(t instanceof ClientboundRotateHeadPacket)) {
                            vsPacket = new PacketEntityShipMotion(entity.getId(), ship.getId(),
                                position.x, position.y, position.z,
                                motion.x, motion.y, motion.z,
                                yaw, pitch);
                        } else {
                            vsPacket = new PacketMobShipRotation(entity.getId(), ship.getId(), yaw, pitch);
                        }

                        vs$sendToTrackedViewers(vsPacket);
                        vs$lastSentShipId = ship.getId();
                        vs$lastSentMotionTick = vs$getServerTick();
                        vs$sentPacketThisTick = true;
                        return;
                    }
                }

                if (vs$lastSentShipId != null) {
                    vs$sendToTrackedViewers(new PacketEntityShipMotion(entity.getId(), -1L, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
                    vs$lastSentShipId = null;
                }
            }
        }
        original.call(instance, t);
    }
}
