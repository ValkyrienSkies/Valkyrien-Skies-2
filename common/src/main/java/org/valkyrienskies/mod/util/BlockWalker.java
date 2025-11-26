package org.valkyrienskies.mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Modified from vanilla BlockGetter.traverseBlocks
 */
public final class BlockWalker {
    private static final double EPS = 1e-7;
    private BlockPos.MutableBlockPos nextValue = new BlockPos.MutableBlockPos();
    private final int xStep;
    private final int yStep;
    private final int zStep;
    private final double xPartialStep;
    private final double yPartialStep;
    private final double zPartialStep;
    private int x;
    private int y;
    private int z;
    private double xPartial;
    private double yPartial;
    private double zPartial;

    public BlockWalker(final Vec3 from, final Vec3 to) {
        if (from.equals(to)) {
            this.nextValue = null;
            this.xStep = 0;
            this.yStep = 0;
            this.zStep = 0;
            this.xPartialStep = 0;
            this.yPartialStep = 0;
            this.zPartialStep = 0;
            return;
        }

        final double afterToX = from.x < to.x ? to.x + EPS : to.x < from.x ? to.x - EPS : to.x;
        final double afterToY = from.y < to.y ? to.y + EPS : to.y < from.y ? to.y - EPS : to.y;
        final double afterToZ = from.z < to.z ? to.z + EPS : to.z < from.z ? to.z - EPS : to.z;
        final double beforeFromX = from.x < to.x ? from.x - EPS : to.x < from.x ? from.x + EPS : from.x;
        final double beforeFromY = from.y < to.y ? from.y - EPS : to.y < from.y ? from.y + EPS : from.y;
        final double beforeFromZ = from.z < to.z ? from.z - EPS : to.z < from.z ? from.z + EPS : from.z;
        this.x = Mth.floor(from.x);
        this.y = Mth.floor(from.y);
        this.z = Mth.floor(from.z);
        final double xDiff = afterToX - beforeFromX;
        final double yDiff = afterToY - beforeFromY;
        final double zDiff = afterToZ - beforeFromZ;
        this.xStep = Mth.sign(xDiff);
        this.yStep = Mth.sign(yDiff);
        this.zStep = Mth.sign(zDiff);
        this.xPartialStep = this.xStep == 0 ? Double.MAX_VALUE : (double) (this.xStep) / xDiff;
        this.yPartialStep = this.yStep == 0 ? Double.MAX_VALUE : (double) (this.yStep) / yDiff;
        this.zPartialStep = this.zStep == 0 ? Double.MAX_VALUE : (double) (this.zStep) / zDiff;
        this.xPartial = this.xPartialStep * (this.xStep > 0 ? 1 - Mth.frac(beforeFromX) : Mth.frac(beforeFromX));
        this.yPartial = this.yPartialStep * (this.yStep > 0 ? 1 - Mth.frac(beforeFromY) : Mth.frac(beforeFromY));
        this.zPartial = this.zPartialStep * (this.zStep > 0 ? 1 - Mth.frac(beforeFromZ) : Mth.frac(beforeFromZ));
    }

    public BlockPos value() {
        return this.nextValue == null ? null : this.nextValue.set(this.x, this.y, this.z);
    }

    public boolean next() {
        if (this.nextValue == null) {
            return false;
        }
        if (this.xPartial > 1 && this.yPartial > 1 && this.zPartial > 1) {
            this.nextValue = null;
            return false;
        }
        if (this.xPartial < this.yPartial) {
            if (this.xPartial < this.zPartial) {
                this.x += this.xStep;
                this.xPartial += this.xPartialStep;
            } else {
                this.z += this.zStep;
                this.zPartial += this.zPartialStep;
            }
        } else if (this.yPartial < this.zPartial) {
            this.y += this.yStep;
            this.yPartial += this.yPartialStep;
        } else {
            this.z += this.zStep;
            this.zPartial += this.zPartialStep;
        }
        return true;
    }
}
