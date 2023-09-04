package org.valkyrienskies.mod.mixin.accessors.world.level.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StairBlock.class)
public interface StairBlockAccessor {
    @Accessor("TOP_SHAPES")
    static VoxelShape[] getTopShapes() {
        throw new AssertionError();
    }

    @Accessor("BOTTOM_SHAPES")
    static VoxelShape[] getBottomShapes() {
        throw new AssertionError();
    }
}
