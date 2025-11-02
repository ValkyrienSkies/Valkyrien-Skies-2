package org.valkyrienskies.mod.compat.create;

public class AirFlowClipContext extends ClipContext {
    private final Level level;
    private final BlockPos source;
    private final Ship sourceShip;

    public AirFlowClipContext(final Level level, final BlockPos source, final Vec3 from, final Vec3 to) {
        super(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        this.level = level;
        this.source = source;
        this.sourceShip = VSGameUtilsKt.getShipManagingPos(level, source);
    }

    @Override
    public VoxelShape getBlockShape(final BlockState state, final BlockGetter level, final BlockPos pos) {
        // Ignore collision check on the same ship since create already handle it in a better way
        if (this.sourceShip == VSGameUtilsKt.getShipManagingPos(this.level, pos)) {
            return Shapes.empty();
        }
        final BlockState copycat = CopycatBlock.getMaterial(level, pos);
        if (shouldAlwaysPass(copycat.isAir() ? state : copycat)) {
            return Shapes.empty();
        }
        return super.getBlockShape(state, level, pos);
    }
}
