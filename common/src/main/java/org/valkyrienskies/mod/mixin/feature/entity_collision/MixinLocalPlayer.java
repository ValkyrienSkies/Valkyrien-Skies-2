package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.networking.PacketPlayerShipMotion;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends Entity implements IEntityDraggingInformationProvider {
    public MixinLocalPlayer(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendPosition(ClientPacketListener instance, Packet<?> arg, Operation<Void> original) {
        if (getDraggingInformation().isEntityBeingDraggedByAShip()) {
            if (getDraggingInformation().getLastShipStoodOn() != null) {
                ClientShip ship = VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance().level).getAllShips().getById(getDraggingInformation().getLastShipStoodOn());
                if (ship != null) {
                    Vector3dc relativePosition = ship.getWorldToShip().transformPosition(
                        VectorConversionsMCKt.toJOML(getEyePosition()), new Vector3d());

                    double relativeYaw = EntityLerper.INSTANCE.yawToShip(ship, getYHeadRot());

                    PacketPlayerShipMotion packet = new PacketPlayerShipMotion(getId(), relativePosition.x(), relativePosition.y(), relativePosition.z(), relativeYaw);
                    ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
                }
            }
        }
        original.call(instance, arg);
    }
}
