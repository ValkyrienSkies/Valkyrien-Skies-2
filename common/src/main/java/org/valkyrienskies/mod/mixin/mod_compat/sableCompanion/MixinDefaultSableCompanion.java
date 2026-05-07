package org.valkyrienskies.mod.mixin.mod_compat.sableCompanion;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = DefaultSableCompanion.class, remap = false)
public abstract class MixinDefaultSableCompanion implements SableCompanion {
    @Override
    public Vector3d projectOutOfSubLevel(Level level, Vector3dc pos, Vector3d dest) {
        Ship ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
        if (ship != null)
            return ship.getTransform().getShipToWorld().transformPosition(pos, dest);
        return dest.set(pos);
    }

    @Override
    public Vec3 projectOutOfSubLevel(Level level, Vec3 pos) {
        return VectorConversionsMCKt.toMinecraft(projectOutOfSubLevel(level, VectorConversionsMCKt.toJOML(pos), new Vector3d()));
    }

    @Override
    public Vec3 projectOutOfSubLevel(Level level, Position pos) {
        return SableCompanion.super.projectOutOfSubLevel(level, pos);
    }

    @Override
    public double distanceSquaredWithSubLevels(Level level, Vector3dc a, Vector3dc b) {
        return distanceSquaredWithSubLevels(level, a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }

    @Override
    public double distanceSquaredWithSubLevels(Level level, Position a, Position b) {
        return distanceSquaredWithSubLevels(level, a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }

    @Override
    public double distanceSquaredWithSubLevels(Level level, Vector3dc a, double bX, double bY, double bZ) {
        return distanceSquaredWithSubLevels(level, a.x(), a.y(), a.z(), bX, bY, bZ);
    }

    @Override
    public double distanceSquaredWithSubLevels(Level level, Position a, double bX, double bY, double bZ) {
        return distanceSquaredWithSubLevels(level, a.x(), a.y(), a.z(), bX, bY, bZ);
    }

    @Override
    public double distanceSquaredWithSubLevels(Level level, double aX, double aY, double aZ, double bX, double bY,
        double bZ) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(level, aX, aY, aZ, bX, bY, bZ);
    }

    @Override
    public Vector3d getVelocity(Level level, Vector3dc pos, Vector3d dest) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null)
            return dest.zero();
        return dest.set(ship.getVelocity());
    }

    @Override
    public Vec3 getVelocity(Level level, Vec3 pos) {
        return VectorConversionsMCKt.toMinecraft(getVelocity(level, VectorConversionsMCKt.toJOML(pos), new Vector3d()));
    }

    @Override
    public boolean isInPlotGrid(Level level, int chunkX, int chunkZ) {
        return VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ);
    }
}
