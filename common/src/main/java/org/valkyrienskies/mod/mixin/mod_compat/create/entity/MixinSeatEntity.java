package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SeatEntity.class)
public abstract class MixinSeatEntity extends Entity {
    public MixinSeatEntity(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * @author Triode
     * @reason Fix dismount position when ship or seat is destroyed
     */
    @Overwrite
    public @NotNull Vec3 getDismountLocationForPassenger(final @NotNull LivingEntity livingEntity) {
        if (VSGameUtilsKt.isBlockInShipyard(level(), position()) && VSGameUtilsKt.getShipManagingPos(level, position()) == null) {
            // Don't teleport to the ship if we can't find the ship
            return livingEntity.position();
        }
        return super.getDismountLocationForPassenger(livingEntity).add(0, 0.5f, 0);
    }
}
