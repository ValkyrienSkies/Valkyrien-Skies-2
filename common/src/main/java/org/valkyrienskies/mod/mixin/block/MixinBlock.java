package org.valkyrienskies.mod.mixin.block;

import net.minecraft.block.Block;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipDataCommon;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Block.class)
public class MixinBlock {

    /**
     * Ensure that items drop in the world and not in the shipyard.
     */
    @Redirect(
        method = "dropStack",
        at = @At(
            value = "NEW",
            target = "net/minecraft/entity/ItemEntity",
            ordinal = 0
        )
    )
    private static ItemEntity moveItemDrops(
        // Constructor arguments
        final World world, final double x, final double y, final double z, final ItemStack stack,
        // dropStack arguments
        final World ignore, final BlockPos pos, final ItemStack ignore2
    ) {
        final ShipDataCommon ship = VSGameUtilsKt.getShipManagingPos(world, pos);
        if (ship == null) {
            // Vanilla behaviour
            return new ItemEntity(world, x, y, z, stack);
        }

        // Extract random offset
        final double dx = x - pos.getX();
        final double dy = y - pos.getY();
        final double dz = z - pos.getZ();

        // Real position of item drop in world
        final Vector3d p = VSGameUtilsKt.toWorldCoordinates(ship, pos);

        return new ItemEntity(world, p.x + dx, p.y + dy, p.z + dz, stack);
    }

}
