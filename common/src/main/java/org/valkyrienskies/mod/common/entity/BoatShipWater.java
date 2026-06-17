package org.valkyrienskies.mod.common.entity;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Ship-frame reimplementation of the boat fluid scans. The whole scan must run
 * in the ship's frame: boat buoyancy settles to an equilibrium sensitive to the
 * exact water surface, so taking cellY in the world frame while sampling
 * getHeight in the ship frame puts that surface off by up to a block on
 * non-integer / multi-deep ship pools and floats the boat visibly wrong. cellY
 * and getHeight must stay in the same frame.
 *
 * <p>The "is this a fluid the boat floats in" test is passed in by the caller so
 * the platform difference (Fabric vanilla {@code is(FluidTags.WATER)} vs Forge's
 * {@code IForgeBoat.canBoatInFluid}) stays out of this shared logic.
 */
public final class BoatShipWater {

    private BoatShipWater() {
    }

    public record CheckInWater(boolean inWater, double waterLevel) {
    }

    public static Ship parentShip(final Boat boat) {
        final Level level = boat.level();
        final AABB box = boat.getBoundingBox();
        final Vector3d probe = new Vector3d(boat.getX(), box.minY - 0.01, boat.getZ());
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, box)) {
            final Vector3d shipPos = ship.getWorldToShip().transformPosition(new Vector3d(probe));
            final BlockPos blockPos = BlockPos.containing(shipPos.x, shipPos.y, shipPos.z);
            if (!level.getBlockState(blockPos).isAir()) {
                return ship;
            }
        }
        return null;
    }

    public static float getWaterLevelAbove(final Boat boat, final Ship ship, final double lastYd,
        final Predicate<FluidState> isBoatFluid) {
        final AABB box = shipBox(ship, boat.getBoundingBox());
        final Level level = boat.level();
        final int i = Mth.floor(box.minX);
        final int j = Mth.ceil(box.maxX);
        final int k = Mth.floor(box.maxY);
        final int l = Mth.ceil(box.maxY - lastYd);
        final int i1 = Mth.floor(box.minZ);
        final int j1 = Mth.ceil(box.maxZ);
        final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        float resultShipY = l + 1;
        label:
        for (int k1 = k; k1 < l; ++k1) {
            float f = 0.0F;
            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    bp.set(l1, k1, i2);
                    final FluidState fs = level.getFluidState(bp);
                    if (isBoatFluid.test(fs)) {
                        f = Math.max(f, fs.getHeight(level, bp));
                    }
                    if (f >= 1.0F) {
                        continue label;
                    }
                }
            }
            if (f < 1.0F) {
                resultShipY = (float) k1 + f;
                break;
            }
        }

        return (float) shipYToWorldY(ship, boat, resultShipY);
    }

    public static CheckInWater checkInWater(final Boat boat, final Ship ship,
        final Predicate<FluidState> isBoatFluid) {
        final AABB box = shipBox(ship, boat.getBoundingBox());
        final Level level = boat.level();
        final int i = Mth.floor(box.minX);
        final int j = Mth.ceil(box.maxX);
        final int k = Mth.floor(box.minY);
        final int l = Mth.ceil(box.minY + 0.001);
        final int i1 = Mth.floor(box.minZ);
        final int j1 = Mth.ceil(box.maxZ);
        final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        boolean flag = false;
        double maxShipSurface = -Double.MAX_VALUE;
        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    bp.set(k1, l1, i2);
                    final FluidState fs = level.getFluidState(bp);
                    if (isBoatFluid.test(fs)) {
                        final double f = l1 + fs.getHeight(level, bp);
                        maxShipSurface = Math.max(f, maxShipSurface);
                        flag |= box.minY < f;
                    }
                }
            }
        }

        final double waterLevel = maxShipSurface == -Double.MAX_VALUE
            ? -Double.MAX_VALUE
            : shipYToWorldY(ship, boat, maxShipSurface);
        return new CheckInWater(flag, waterLevel);
    }

    public static Boat.Status isUnderwater(final Boat boat, final Ship ship,
        final Predicate<FluidState> isBoatFluid) {
        final AABB box = shipBox(ship, boat.getBoundingBox());
        final Level level = boat.level();
        final double d0 = box.maxY + 0.001;
        final int i = Mth.floor(box.minX);
        final int j = Mth.ceil(box.maxX);
        final int k = Mth.floor(box.maxY);
        final int l = Mth.ceil(d0);
        final int i1 = Mth.floor(box.minZ);
        final int j1 = Mth.ceil(box.maxZ);
        final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        boolean flag = false;
        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    bp.set(k1, l1, i2);
                    final FluidState fs = level.getFluidState(bp);
                    if (isBoatFluid.test(fs) && d0 < (double) ((float) bp.getY() + fs.getHeight(level, bp))) {
                        if (!fs.isSource()) {
                            return Boat.Status.UNDER_FLOWING_WATER;
                        }
                        flag = true;
                    }
                }
            }
        }

        return flag ? Boat.Status.UNDER_WATER : null;
    }

    public static float getGroundFriction(final Boat boat, final Ship ship) {
        final AABB box = shipBox(ship, boat.getBoundingBox());
        final Level level = boat.level();
        final AABB slab = new AABB(box.minX, box.minY - 0.001, box.minZ, box.maxX, box.minY, box.maxZ);
        final int i = Mth.floor(slab.minX) - 1;
        final int j = Mth.ceil(slab.maxX) + 1;
        final int k = Mth.floor(slab.minY) - 1;
        final int l = Mth.ceil(slab.maxY) + 1;
        final int i1 = Mth.floor(slab.minZ) - 1;
        final int j1 = Mth.ceil(slab.maxZ) + 1;
        final VoxelShape boatShape = Shapes.create(slab);
        float friction = 0.0F;
        int count = 0;
        final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        for (int l1 = i; l1 < j; ++l1) {
            for (int i2 = i1; i2 < j1; ++i2) {
                final int edge = (l1 != i && l1 != j - 1 ? 0 : 1) + (i2 != i1 && i2 != j1 - 1 ? 0 : 1);
                if (edge != 2) {
                    for (int k2 = k; k2 < l; ++k2) {
                        if (edge <= 0 || k2 != k && k2 != l - 1) {
                            bp.set(l1, k2, i2);
                            final BlockState bs = level.getBlockState(bp);
                            if (!(bs.getBlock() instanceof WaterlilyBlock) && Shapes.joinIsNotEmpty(
                                bs.getCollisionShape(level, bp).move(l1, k2, i2), boatShape, BooleanOp.AND)) {
                                friction += bs.getBlock().getFriction();
                                ++count;
                            }
                        }
                    }
                }
            }
        }

        return count == 0 ? 0.0F : friction / (float) count;
    }

    private static AABB shipBox(final Ship ship, final AABB worldBox) {
        final Matrix4dc w2s = ship.getWorldToShip();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            final double x = (c & 1) == 0 ? worldBox.minX : worldBox.maxX;
            final double y = (c & 2) == 0 ? worldBox.minY : worldBox.maxY;
            final double z = (c & 4) == 0 ? worldBox.minZ : worldBox.maxZ;
            final Vector3d p = w2s.transformPosition(new Vector3d(x, y, z));
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
            minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double shipYToWorldY(final Ship ship, final Boat boat, final double shipSurfaceY) {
        final Vector3d bShip = ship.getWorldToShip().transformPosition(
            new Vector3d(boat.getX(), boat.getY(), boat.getZ()));
        return ship.getShipToWorld().transformPosition(
            new Vector3d(bShip.x, shipSurfaceY, bShip.z)).y;
    }
}
