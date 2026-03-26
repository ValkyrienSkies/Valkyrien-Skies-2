package org.valkyrienskies.mod.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectiond;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * traverseBlocks on all intersected ships and the world sorted from the nearest block to the furtherest.
 */
public final class AdvancedBlockWalker implements Iterator<AdvancedBlockWalker.BlockPosWithDistance> {
    private final Vec3 from;
    private final Vec3 to;
    private final boolean reversed;
    private final boolean useFurtherestClip;
    private final double maxDist;
    private final ArrayList<BlockWalkerWithShip> walkers = new ArrayList<>();

    public AdvancedBlockWalker(final Level level, Vec3 from, Vec3 to, final boolean reversed, final boolean useFurtherestClip) {
        if (reversed) {
            final Vec3 tmp = from;
            from = to;
            to = tmp;
        }
        this.from = from = VSGameUtilsKt.toWorldCoordinates(level, from);
        this.to = to = VSGameUtilsKt.toWorldCoordinates(level, to);
        this.reversed = reversed;
        this.useFurtherestClip = useFurtherestClip;
        this.maxDist = from.distanceTo(to);
        {
            final BlockWalkerWithShip walker = new BlockWalkerWithShip(new BlockWalker(from, to), null);
            if (walker.value() != null) {
                this.walkers.add(walker);
            }
        }
        final AABB worldBox = new AABB(from, to);
        final AABBd tmpBox = new AABBd();
        final AABBd tmpBox2 = new AABBd();
        final Vector3d fromVec = new Vector3d();
        final Vector3d toVec = new Vector3d();
        final Vector2d tmp2d = new Vector2d();
        for (final Ship ship : VSGameUtilsKt.getAllShips(level)) {
            final AABBdc shipBox1 = ship.getWorldAABB();
            if (
                !tmpBox2
                    .setMin(shipBox1.minX() - 1, shipBox1.minY() - 1, shipBox1.minZ() - 1)
                    .setMax(shipBox1.maxX() + 1, shipBox1.maxY() + 1, shipBox1.maxZ() + 1)
                    .intersectsAABB(
                        tmpBox
                            .setMin(worldBox.minX, worldBox.minY, worldBox.minZ)
                            .setMax(worldBox.maxX, worldBox.maxY, worldBox.maxZ)
                    )
            ) {
                continue;
            }
            final AABBic shipBox2 = ship.getShipAABB();
            if (shipBox2 == null) {
                continue;
            }
            ship.getWorldToShip().transformPosition(fromVec.set(from.x, from.y, from.z));
            ship.getWorldToShip().transformPosition(toVec.set(to.x, to.y, to.z));
            final int intersectResult = tmpBox
                .setMin(shipBox2.minX() - 1, shipBox2.minY() - 1, shipBox2.minZ() - 1)
                .setMax(shipBox2.maxX() + 2, shipBox2.maxY() + 2, shipBox2.maxZ() + 2)
                .intersectsLineSegment(
                    fromVec.x, fromVec.y, fromVec.z,
                    toVec.x, toVec.y, toVec.z,
                    tmp2d
                );
            if (intersectResult == Intersectiond.OUTSIDE) {
                continue;
            }
            final BlockWalkerWithShip walker = new BlockWalkerWithShip(new BlockWalker(
                new Vec3(fromVec.x, fromVec.y, fromVec.z),
                new Vec3(toVec.x, toVec.y, toVec.z)
            ), ship);
            if (walker.value() != null) {
                this.walkers.add(walker);
            }
        }
    }

    @Override
    public boolean hasNext() {
        return !this.walkers.isEmpty();
    }

    @Override
    public BlockPosWithDistance next() {
        final int size = this.walkers.size();
        if (size == 0) {
            throw new NoSuchElementException();
        }
        BlockWalkerWithShip walker = this.walkers.get(0);
        int walkerIndex = 0;
        double dist = this.calcDistance(walker.value(), walker.ship());
        for (int i = 1; i < size; i++) {
            final BlockWalkerWithShip w = this.walkers.get(i);
            final double d = this.calcDistance(w.value(), w.ship());
            if (d < dist) {
                walker = w;
                walkerIndex = i;
                dist = d;
            }
        }
        final BlockPos pos = walker.value().immutable();
        if (!walker.next()) {
            final int lastIndex = size - 1;
            this.walkers.set(walkerIndex, this.walkers.get(lastIndex));
            this.walkers.remove(lastIndex);
        }
        final double dist0 = Math.sqrt(dist);
        return new BlockPosWithDistance(
            pos,
            this.reversed
                ? Math.max(this.maxDist - dist0, 0)
                : Math.min(dist0, this.maxDist)
        );
    }

    private double calcDistance(final BlockPos pos, final Ship ship) {
        final AABB block = new AABB(pos).inflate(1e-7);
        Vec3 from = this.from, to = this.to;
        if (this.reversed != this.useFurtherestClip) {
            // clip reversely to get the furtherest point of the clip
            final Vec3 tmp = from;
            from = to;
            to = tmp;
        }
        if (ship != null) {
            final Vector3d fromVec = ship.getWorldToShip().transformPosition(new Vector3d(from.x, from.y, from.z));
            final Vector3d toVec = ship.getWorldToShip().transformPosition(new Vector3d(to.x, to.y, to.z));
            from = new Vec3(fromVec.x, fromVec.y, fromVec.z);
            to = new Vec3(toVec.x, toVec.y, toVec.z);
        }
        if (block.contains(from)) {
            return this.reversed == this.useFurtherestClip ? 0 : this.maxDist * this.maxDist;
        }
        Vec3 point = block.clip(from, to).orElse(null);
        if (point == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (ship != null) {
            final Vector3d pointVec = ship.getShipToWorld().transformPosition(new Vector3d(point.x, point.y, point.z));
            point = new Vec3(pointVec.x, pointVec.y, pointVec.z);
        }
        return point.distanceToSqr(this.from);
    }

    private record BlockWalkerWithShip(BlockWalker walker, Ship ship) {
        BlockWalkerWithShip {
            if (walker.value() != null) {
                while (!withinShipBorder(ship, walker.value()) && walker.next());
            }
        }

        private boolean withinBorder(final BlockPos pos) {
            return withinShipBorder(this.ship, pos);
        }

        private static boolean withinShipBorder(final Ship ship, final BlockPos pos) {
            if (ship == null) {
                return true;
            }
            final AABBic shipBox = ship.getShipAABB();
            if (shipBox == null) {
                return false;
            }
            final int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            return shipBox.minX() - 1 <= x && x < shipBox.maxX() + 2 &&
                shipBox.minY() - 1 <= y && y < shipBox.maxY() + 2 &&
                shipBox.minZ() - 1 <= z && z < shipBox.maxZ() + 2;
        }

        public BlockPos value() {
            return this.walker.value();
        }

        public boolean next() {
            while (this.walker.next()) {
                if (this.withinBorder(this.value())) {
                    return true;
                }
            }
            return false;
        }
    }

    public record BlockPosWithDistance(BlockPos pos, double distance) {}
}
