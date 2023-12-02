package org.valkyrienskies.mod.mixin.feature.entity_rubberband_fix;

import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.mixinducks.feature.fix_entity_rubberband.ClientboundMoveEntityPacketDuck;
import org.valkyrienskies.mod.mixinducks.feature.fix_entity_rubberband.ClientboundTeleportEntityPacketDuck;

@Mixin(ServerEntity.class)
public class MixinServerEntity {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "sendChanges", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 3), locals = LocalCapture.CAPTURE_FAILHARD)
    private void preSendChanges(CallbackInfo ci, List list, int i, int j, Vec3 vec3, boolean bl2, Packet packet2) {
        if (packet2 instanceof ClientboundMoveEntityPacket) {
            //final ServerEntity entity = (ServerEntity) (Object) this;
            //final Entity normalEntity = (Entity) entity;
            final Entity entity = ((ClientboundMoveEntityPacket) packet2).getEntity(level);
            final EntityDraggingInformation draggingInformation = ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
            if (draggingInformation.isEntityBeingDraggedByAShip()) {
                ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setShipId(draggingInformation.getLastShipStoodOn());

                Long shipId = draggingInformation.getLastShipStoodOn();
                if (shipId != null) {
                    ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(shipId);
//                    Vector3dc moveVecModified = new Vector3d(((ClientboundMoveEntityPacket) packet2).getXa()/4096.0 + draggingInformation.getAddedMovementLastTick().x(), ((ClientboundMoveEntityPacket) packet2).getYa()/4096.0 + draggingInformation.getAddedMovementLastTick().y(), ((ClientboundMoveEntityPacket) packet2).getZa()/4096.0 + draggingInformation.getAddedMovementLastTick().z());
//
////                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setXa((int) (moveVecModified.x() * 4096));
////                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setYa((int) (moveVecModified.y() * 4096));
////                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setZa((int) (moveVecModified.z() * 4096));

                    Vector3dc moveVecTransformed = ship.getTransform().getWorldToShip().transformDirection(new Vector3d(((ClientboundMoveEntityPacket) packet2).getXa()/4096.0, ((ClientboundMoveEntityPacket) packet2).getYa()/4096.0, ((ClientboundMoveEntityPacket) packet2).getZa()/4096.0));
                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setXa((short) (moveVecTransformed.x() * 4096));
                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setYa((short) (moveVecTransformed.y() * 4096));
                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setZa((short) (moveVecTransformed.z() * 4096));
                }
            }

        } else if (packet2 instanceof ClientboundTeleportEntityPacket) {
            final Entity entity = level.getEntity(((ClientboundTeleportEntityPacket) packet2).getId());
            final EntityDraggingInformation draggingInformation = ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
            if (draggingInformation.isEntityBeingDraggedByAShip()) {
                ((ClientboundTeleportEntityPacketDuck) packet2).valkyrienskies$setShipId(draggingInformation.getLastShipStoodOn());

                Long shipId = draggingInformation.getLastShipStoodOn();
                if (shipId != null) {
                    ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(shipId);
//                    Vector3dc moveVecModified = new Vector3d(((ClientboundMoveEntityPacket) packet2).getXa()/4096.0 + draggingInformation.getAddedMovementLastTick().x(), ((ClientboundMoveEntityPacket) packet2).getYa()/4096.0 + draggingInformation.getAddedMovementLastTick().y(), ((ClientboundMoveEntityPacket) packet2).getZa()/4096.0 + draggingInformation.getAddedMovementLastTick().z());
//
////                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setXa((int) (moveVecModified.x() * 4096));
////                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setYa((int) (moveVecModified.y() * 4096));
////                    ((ClientboundMoveEntityPacketDuck) packet2).valkyrienskies$setZa((int) (moveVecModified.z() * 4096));

                    Vector3dc moveVecTransformed = ship.getTransform().getWorldToShip().transformPosition(new Vector3d(((ClientboundTeleportEntityPacket) packet2).getX(), ((ClientboundTeleportEntityPacket) packet2).getY(), ((ClientboundTeleportEntityPacket) packet2).getZ()));
                    ((ClientboundTeleportEntityPacketDuck) packet2).valkyrienskies$setX(moveVecTransformed.x());
                    ((ClientboundTeleportEntityPacketDuck) packet2).valkyrienskies$setY(moveVecTransformed.y());
                    ((ClientboundTeleportEntityPacketDuck) packet2).valkyrienskies$setZ(moveVecTransformed.z());
                }
            }
        }
    }
}
