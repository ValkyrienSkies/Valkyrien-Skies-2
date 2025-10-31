package org.valkyrienskies.mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Modified from vanilla BlockGetter.traverseBlocks
 */
public final class BlockWalker {
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

        final double afterToX = Mth.lerp(-1e-7, to.x, from.x);
        final double afterToY = Mth.lerp(-1e-7, to.y, from.y);
        final double afterToZ = Mth.lerp(-1e-7, to.z, from.z);
        final double beforeFromX = Mth.lerp(-1e-7, from.x, to.x);
        final double beforeFromY = Mth.lerp(-1e-7, from.y, to.y);
        final double beforeFromZ = Mth.lerp(-1e-7, from.z, to.z);
        this.x = Mth.floor(beforeFromX);
        this.y = Mth.floor(beforeFromY);
        this.z = Mth.floor(beforeFromZ);
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
