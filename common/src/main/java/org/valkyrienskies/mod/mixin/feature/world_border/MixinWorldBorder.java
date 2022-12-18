package org.valkyrienskies.mod.mixin.feature.world_border;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(WorldBorder.class)
public class MixinWorldBorder implements OfLevel {

    @Unique
    private Level level;

    @ModifyReturnValue(
        method = "isWithinBounds(Lnet/minecraft/core/BlockPos;)Z",
        at = @At("RETURN")
    )
    public boolean shipsWithinBounds(final boolean isWithinBounds, final BlockPos pos) {
        return isWithinBounds || (level != null && VSGameUtilsKt.getShipManagingPos(level, pos) != null);
    }

    @ModifyReturnValue(
        method = "isWithinBounds(Lnet/minecraft/world/level/ChunkPos;)Z",
        at = @At("RETURN")
    )
    public boolean shipsWithinBounds(final boolean isWithinBounds, final ChunkPos pos) {
        return isWithinBounds || (level != null && VSGameUtilsKt.getShipManagingPos(level, pos) != null);
    }

    @ModifyReturnValue(
        method = "isWithinBounds(DD)Z",
        at = @At("RETURN")
    )
    public boolean shipsWithinBounds(final boolean isWithinBounds, final double x, final double z) {
        return isWithinBounds ||
            (level != null && VSGameUtilsKt.getShipManagingPos(level, (int) x >> 4, (int) z >> 4) != null);
    }

    @ModifyReturnValue(
        method = "isWithinBounds(DDD)Z",
        at = @At("RETURN")
    )
    public boolean shipsWithinBounds(final boolean isWithinBounds, final double x, final double z,
        final double offset) {
        return isWithinBounds ||
            (level != null && VSGameUtilsKt.getShipManagingPos(level, (int) x >> 4, (int) z >> 4) != null);
    }

    @ModifyReturnValue(
        method = "isWithinBounds(Lnet/minecraft/world/phys/AABB;)Z",
        at = @At("RETURN")
    )
    public boolean shipsWithinBounds(final boolean isWithinBounds, final AABB aabb) {
        if (isWithinBounds) {
            return true;
        }

        final Ship s1 = VSGameUtilsKt.getShipManagingPos(level, (int) aabb.minX >> 4, (int) aabb.minZ >> 4);
        final Ship s2 = VSGameUtilsKt.getShipManagingPos(level, (int) aabb.maxX >> 4, (int) aabb.maxZ >> 4);

        return s1 != null && s2 != null && s1.getId() == s2.getId();
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public void setLevel(final Level level) {
        this.level = level;
    }
}
