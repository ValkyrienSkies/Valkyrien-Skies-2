package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SeatEntity.class)
public abstract class MixinSeatEntity extends Entity {

    @Shadow
    public static double getCustomEntitySeatOffset(Entity entity) {
        return 0;
    }

    public MixinSeatEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void positionRider(@NotNull Entity passenger, MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger))
            return;

        double d0 = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
        Vec3 riderPos = new Vec3(this.getX(), d0 + getCustomEntitySeatOffset(passenger), this.getZ());

        Ship ship = VSGameUtilsKt.getShipManagingPos(passenger.level(), riderPos.x, riderPos.y, riderPos.z);
        if (VSGameUtilsKt.isBlockInShipyard(passenger.level(), riderPos.x, riderPos.y, riderPos.z) && ship != null) {
            Vector3d tempVec = VectorConversionsMCKt.toJOML(riderPos);
            ship.getShipToWorld().transformPosition(tempVec, tempVec);
            riderPos = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        passenger.setPos(riderPos);
    }
}
