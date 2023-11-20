package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SeatEntity.class)
public abstract class MixinSeatEntity extends Entity {

    @Shadow
    public static double getCustomEntitySeatOffset(final Entity entity) {
        return 0;
    }

    public MixinSeatEntity(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * @author Triode
     * @reason Fix rider position
     */
    @Overwrite
    public void positionRider(@NotNull final Entity passenger, final Entity.MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger))
            return;

        final double d0 = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
        Vec3 riderPos = new Vec3(this.getX(), d0 + getCustomEntitySeatOffset(passenger), this.getZ());

        final Ship ship = VSGameUtilsKt.getShipManagingPos(passenger.level, riderPos.x, riderPos.y, riderPos.z);
        if (VSGameUtilsKt.isBlockInShipyard(passenger.level, riderPos.x, riderPos.y, riderPos.z) && ship != null) {
            final Vector3d tempVec = VectorConversionsMCKt.toJOML(riderPos);
            ship.getShipToWorld().transformPosition(tempVec, tempVec);
            riderPos = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        passenger.setPos(riderPos);
    }

    /**
     * @author Triode
     * @reason Fix dismount position when ship or seat is destroyed
     */
    @Overwrite
    public @NotNull Vec3 getDismountLocationForPassenger(final @NotNull LivingEntity livingEntity) {
        if (VSGameUtilsKt.isBlockInShipyard(level, position()) && VSGameUtilsKt.getShipManagingPos(level, position()) == null) {
            // Don't teleport to the ship if we can't find the ship
            return livingEntity.position();
        }
        return super.getDismountLocationForPassenger(livingEntity).add(0, 0.5f, 0);
    }
}
