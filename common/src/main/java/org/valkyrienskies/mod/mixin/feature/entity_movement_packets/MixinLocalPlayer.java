package org.valkyrienskies.mod.mixin.feature.entity_movement_packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Objects;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.networking.PacketPlayerShipMotion;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends Entity {
    @Shadow private int positionReminder;
    @Unique private Long vs$lastSentShip;

    public MixinLocalPlayer(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void vs$sendFrameChangesImmediately(CallbackInfo ci) {
        final EntityDraggingInformation info = ((IEntityDraggingInformationProvider) this).getDraggingInformation();
        final Long shipId = info.isEntityBeingDraggedByAShip() ? info.getLastShipStoodOn() : null;
        if (!Objects.equals(shipId, vs$lastSentShip)) positionReminder = 20;
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendPosition(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
        if (packet instanceof ServerboundMovePlayerPacket movement) {
            if (vs$sendShipMotion(this, -1, movement.isOnGround())) return;
            vs$lastSentShip = null;
        }
        original.call(connection, packet);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendVehiclePosition(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
        final Entity vehicle = getRootVehicle();
        if (packet instanceof ServerboundMoveVehiclePacket && vehicle != this
            && vehicle.getControllingPassenger() == (Object) this
            && vs$sendShipMotion(vehicle, vehicle.getId(), vehicle.onGround())) return;
        original.call(connection, packet);
    }

    @Unique
    private boolean vs$sendShipMotion(Entity entity, int vehicleId, boolean onGround) {
        final IEntityDraggingInformationProvider provider = (IEntityDraggingInformationProvider) entity;
        final EntityDraggingInformation info = provider.getDraggingInformation();
        if (!provider.vs$shouldDrag() || !info.isEntityBeingDraggedByAShip()
            || info.getLastShipStoodOn() == null) return false;
        final Ship ship = VSGameUtilsKt.getShipObjectWorld(level()).getAllShips()
            .getById(info.getLastShipStoodOn());
        if (ship == null) return false;
        final Vector3d position = ship.getWorldToShip()
            .transformPosition(new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
        ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(new PacketPlayerShipMotion(
            ship.getId(), position.x, position.y, position.z,
            EntityLerper.INSTANCE.yawToShip(ship, entity.getYRot()), entity.getXRot(), onGround,
            info.getTicksSinceStoodOnShip(), vehicleId));
        if (vehicleId == -1) vs$lastSentShip = ship.getId();
        return true;
    }
}
