package org.valkyrienskies.mod.compat.create;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class AirFlowClipContext extends ClipContext {
    private final Level level;
    private final BlockPos source;
    private final Ship sourceShip;
    private final Predicate<BlockState> tester;

    public AirFlowClipContext(final Level level, final BlockPos source, final Vec3 from, final Vec3 to, final Predicate<BlockState> tester) {
        super(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        this.level = level;
        this.source = source;
        this.sourceShip = VSGameUtilsKt.getShipManagingPos(level, source);
        this.tester = tester;
    }

    @Override
    public VoxelShape getBlockShape(final BlockState state, final BlockGetter level, final BlockPos pos) {
        // Ignore collision check on the same ship since create already handle it in a better way
        if (this.sourceShip == VSGameUtilsKt.getShipManagingPos(this.level, pos)) {
            return Shapes.empty();
        }
        final BlockState copycat = CopycatBlock.getMaterial(level, pos);
        if (this.tester.test(copycat.isAir() ? state : copycat)) {
            return Shapes.empty();
        }
        return super.getBlockShape(state, level, pos);
    }
}
