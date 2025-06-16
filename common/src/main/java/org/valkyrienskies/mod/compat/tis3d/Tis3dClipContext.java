package org.valkyrienskies.mod.compat.tis3d;

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

public class Tis3dClipContext extends ClipContext {
    private final CollisionContext collisionContext;

    public Tis3dClipContext(final Vec3 arg, final Vec3 arg2, final Block arg3, final Fluid arg4,
        @Nullable final Entity arg5) {
        super(arg, arg2, arg3, arg4, arg5);
        this.collisionContext = arg5 == null ? CollisionContext.empty() : CollisionContext.of(arg5);
    }

    @Override
    public @NotNull VoxelShape getBlockShape(final @NotNull BlockState bs, final @NotNull BlockGetter bg, final @NotNull BlockPos bp) {
        final VoxelShape collider = Block.COLLIDER.get(bs, bg, bp, this.collisionContext);
        final VoxelShape visual = Block.VISUAL.get(bs, bg, bp, this.collisionContext);
        return Shapes.join(collider, visual, BooleanOp.AND);
    }
}
