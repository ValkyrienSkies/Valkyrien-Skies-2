package org.valkyrienskies.mod.mixin.feature.entity_movement_packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.networking.PacketEntityShipMotion;
import org.valkyrienskies.mod.common.networking.PacketPlayerShipMotion;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends Entity implements IEntityDraggingInformationProvider {
    @Shadow
    public abstract float getViewYRot(float f);

    public MixinLocalPlayer(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @author Tomato
     * @reason Intercept client -> server player position sending to send our own data.
     */
    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendPosition(ClientPacketListener instance, Packet<?> arg, Operation<Void> original) {
        Packet<?> realArg = arg;
        if (getDraggingInformation().isEntityBeingDraggedByAShip()) {
            if (getDraggingInformation().getLastShipStoodOn() != null) {
                ClientShip ship = VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance().level).getAllShips().getById(getDraggingInformation().getLastShipStoodOn());
                if (ship != null) {
                    Vector3dc relativePosition = ship.getWorldToShip().transformPosition(
                        VectorConversionsMCKt.toJOML(getPosition(1f)), new Vector3d());

                    double relativeYaw = EntityLerper.INSTANCE.yawToShip(ship, getViewYRot(1f));

                    PacketPlayerShipMotion packet = new PacketPlayerShipMotion(ship.getId(), relativePosition.x(), relativePosition.y(), relativePosition.z(), relativeYaw);
                    ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
                }
            }
            if (realArg instanceof ServerboundMovePlayerPacket movePacket) {
                final boolean isOnGround = movePacket.isOnGround() || getDraggingInformation().isEntityBeingDraggedByAShip();
                if (movePacket.hasPosition() && movePacket.hasRotation()) {
                    //posrot
                    realArg = new ServerboundMovePlayerPacket.PosRot(movePacket.getX(0.0), movePacket.getY(0.0), movePacket.getZ(0.0), movePacket.getYRot(0.0f), movePacket.getXRot(0.0f), isOnGround);
                } else if (movePacket.hasPosition()) {
                    //pos
                    realArg = new ServerboundMovePlayerPacket.Pos(movePacket.getX(0.0), movePacket.getY(0.0), movePacket.getZ(0.0), isOnGround);
                } else if (movePacket.hasRotation()) {
                    //rot
                    realArg = new ServerboundMovePlayerPacket.Rot(movePacket.getYRot(0.0f), movePacket.getXRot(0.0f), isOnGround);
                } else {
                    //status only
                    realArg = new ServerboundMovePlayerPacket.StatusOnly(isOnGround());
                }
            }
        }
        original.call(instance, realArg);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendVehiclePosition(ClientPacketListener instance, Packet<?> arg, Operation<Void> original) {
        if (arg instanceof ServerboundMoveVehiclePacket vehiclePacket && getRootVehicle() instanceof IEntityDraggingInformationProvider dragProvider) {
            if (dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
                if (dragProvider.getDraggingInformation().getLastShipStoodOn() != null) {
                    ClientShip ship = VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance().level).getAllShips().getById(
                        getDraggingInformation().getLastShipStoodOn());
                    if (ship != null) {
                        Vector3dc relativePosition = ship.getWorldToShip().transformPosition(
                            VectorConversionsMCKt.toJOML(getRootVehicle().getPosition(1f)), new Vector3d());

                        double relativeYaw = EntityLerper.INSTANCE.yawToShip(ship, getRootVehicle().getYRot());

                        PacketEntityShipMotion packet = new PacketEntityShipMotion(getRootVehicle().getId(), ship.getId(), relativePosition.x(), relativePosition.y(), relativePosition.z(), 0.0, 0.0, 0.0, relativeYaw, 0.0);
                        ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
                    }
                }
            }
        }
        original.call(instance, arg);
    }

}
