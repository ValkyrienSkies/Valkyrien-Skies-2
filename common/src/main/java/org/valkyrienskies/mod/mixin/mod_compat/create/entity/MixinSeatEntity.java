package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.ShipMountedToDataProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SeatEntity.class)
public abstract class MixinSeatEntity extends Entity implements ShipMountedToDataProvider {
    public MixinSeatEntity(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * @author Triode
     * @reason Fix dismount position when ship or seat is destroyed
     */
    @Overwrite
    public @NotNull Vec3 getDismountLocationForPassenger(final @NotNull LivingEntity livingEntity) {
        final LoadedShip shipMountedTo = VSGameUtilsKt.getShipMountedTo(livingEntity);
        if (shipMountedTo == null) {
            if (VSGameUtilsKt.isBlockInShipyard(level(), position()) && VSGameUtilsKt.getShipManagingPos(level(), position()) == null) {
                // Don't teleport to the ship if we can't find the ship
                return livingEntity.position();
            }
            return super.getDismountLocationForPassenger(livingEntity).add(0, 0.5f, 0);
        }

        final Vec3 dismountLocationInShip = super.getDismountLocationForPassenger(livingEntity).add(0, 0.5f, 0);
        final Vector3d dismountLocationInWorld = shipMountedTo.getTransform().getShipToWorld()
            .transformPosition(VectorConversionsMCKt.toJOML(dismountLocationInShip), new Vector3d());
        return new Vec3(dismountLocationInWorld.x, dismountLocationInWorld.y, dismountLocationInWorld.z);
    }

    @Override
    public ShipMountedToData provideShipMountedToData(final @NotNull Entity passenger, final Float partialTicks) {
        final LoadedShip shipMountedTo = VSGameUtilsKt.getLoadedShipManagingPos(level(), position().x, position().y, position().z);
        if (shipMountedTo == null) {
            return null;
        }

        final Vec3[] mountPosInShip = new Vec3[1];
        this.positionRider(passenger, (entity, x, y, z) -> mountPosInShip[0] = new Vec3(x, y, z));
        if (mountPosInShip[0] == null) {
            return null;
        }
        return new ShipMountedToData(shipMountedTo, VectorConversionsMCKt.toJOML(mountPosInShip[0]));
    }
}
