package org.valkyrienskies.mod.mixin.feature.move_block_items_drops;

import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Block.class)
public class MixinBlock {

//    /** TODO: PORT
//     * Ensure that items drop in the world and not in the shipyard.
//     */
//    @Redirect(
//        method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
//        at = @At(
//            value = "INVOKE",
//            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
//        )
//    )
//    private static ItemEntity moveItemDrops(
//        // Constructor arguments
//        final Level level, final double x, final double y, final double z, final ItemStack stack,
//        // popResource arguments
//        final Level ignore, final BlockPos pos, final ItemStack ignore2
//    ) {
//        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
//        if (ship == null) {
//            // Vanilla behaviour
//            return new ItemEntity(level, x, y, z, stack);
//        }
//
//        // Extract random offset
//        final double dx = x - pos.getX();
//        final double dy = y - pos.getY();
//        final double dz = z - pos.getZ();
//
//        // Real position of item drop in world
//        final Vector3d p = VSGameUtilsKt.toWorldCoordinates(ship, pos);
//
//        return new ItemEntity(level, p.x + dx, p.y + dy, p.z + dz, stack);
//    }

}
