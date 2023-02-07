package org.valkyrienskies.mod.forge.mixin.compat.tis3d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CustomClipContext extends ClipContext {
    public CustomClipContext(final Vec3 arg, final Vec3 arg2, final Block arg3, final Fluid arg4,
        @Nullable final Entity arg5) {
        super(arg, arg2, arg3, arg4, arg5);
    }

    @Override
    public @NotNull VoxelShape getBlockShape(final BlockState bs, final BlockGetter bg, final BlockPos bp) {
        final VoxelShape collision = Block.COLLIDER.get(bs, bg, bp, (CollisionContext) this);
        final VoxelShape visual = Block.VISUAL.get(bs, bg, bp, (CollisionContext) this);
        return Shapes.join(collision, visual, BooleanOp.AND);
    }
}
