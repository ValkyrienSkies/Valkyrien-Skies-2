package org.valkyrienskies.mod.mixin.feature.move_block_items_drops;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Block.class)
public class MixinBlock {

    /**
     * Ensure that items drop in the world and not in the shipyard.
     */
    @Redirect(
        method = "popResource",
        at = @At(
            value = "NEW",
            target = "net/minecraft/world/entity/item/ItemEntity",
            ordinal = 0
        )
    )
    private static ItemEntity moveItemDrops(
        // Constructor arguments
        final Level level, final double x, final double y, final double z, final ItemStack stack,
        // popResource arguments
        final Level ignore, final BlockPos pos, final ItemStack ignore2
    ) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            // Vanilla behaviour
            return new ItemEntity(level, x, y, z, stack);
        }

        // Extract random offset
        final double dx = x - pos.getX();
        final double dy = y - pos.getY();
        final double dz = z - pos.getZ();

        // Real position of item drop in world
        final Vector3d p = VSGameUtilsKt.toWorldCoordinates(ship, pos);

        return new ItemEntity(level, p.x + dx, p.y + dy, p.z + dz, stack);
    }

}
