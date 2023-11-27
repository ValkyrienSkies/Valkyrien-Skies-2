package org.valkyrienskies.mod.mixin.accessors.world.level.block;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SlabBlock.class)
public interface SlabBlockAccessor {
    @Accessor("BOTTOM_AABB")
    static VoxelShape getBottomAABB() {
        throw new AssertionError();
    }

    @Accessor("TOP_AABB")
    static VoxelShape getTopAABB() {
        throw new AssertionError();
    }
}
