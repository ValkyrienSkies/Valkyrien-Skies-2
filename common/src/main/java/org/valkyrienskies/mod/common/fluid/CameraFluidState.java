package org.valkyrienskies.mod.common.fluid;

import java.lang.ref.WeakReference;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;

/** Point queries in the same interpolated space as the rendered fluid surfaces. */
public final class CameraFluidState {
    private CameraFluidState() {
    }

    public static Sample sample(
        final ClientLevel level, final Vec3 position, final FluidState vanilla,
        final boolean renderRetainedFog
    ) {
        return sample(level, VSGameUtilsKt.getShipObjectWorld(level), position, vanilla, renderRetainedFog);
    }

    static Sample sample(
        final ClientLevel level, final VsiClientShipWorld world, final Vec3 position,
        final FluidState vanilla, final boolean renderRetainedFog
    ) {
        final Vector3d local = new Vector3d();
        FluidState shipBlockFluid = null;
        boolean dryDomain = false;
        boolean hiddenRetainedFluid = false;
        for (final ClientShip ship : world.getLoadedShips()) {
            // The simulation broadphase can miss a ship's interpolated position entirely.
            if (!ship.getRenderAABB().containsPoint(position.x, position.y, position.z)) continue;
            ship.getRenderTransform().getWorldToShip().transformPosition(
                position.x, position.y, position.z, local
            );
            final int x = ShipFluidInteraction.floorToInt(local.x);
            final int y = ShipFluidInteraction.floorToInt(local.y);
            final int z = ShipFluidInteraction.floorToInt(local.z);
            final var pair = world.getFluidGameplaySnapshot(ship.getId());
            final FloodedFluidClientCache.CachedSnapshot flooding =
                FloodedFluidClientCache.get(world, ship.getId(), pair);
            final VsiFluidFloodedVoxel voxel = flooding == null ? null : flooding.get(x, y, z);
            if (voxel != null && FloodedFluidClientCache.isLocallySubmerged(voxel.getFillAmount(), local.y - y)) {
                final FlowingFluid fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(voxel.getFluidId());
                if (fluid != null) {
                    if (renderRetainedFog) return new Sample(fluid.defaultFluidState(), true, false);
                    hiddenRetainedFluid = true;
                    continue;
                }
            }
            final FluidTopologyClientCache.CachedSnapshot topology =
                FluidTopologyClientCache.get(world, ship.getId(), pair);
            if (topology != null && topology.get(x, y, z) != null) {
                dryDomain = true;
                continue;
            }
            if (vanilla.isEmpty() && shipBlockFluid == null) {
                final BlockPos block = new BlockPos(x, y, z);
                final FluidState fluid = level.getFluidState(block);
                if (!fluid.isEmpty() && local.y - y <= fluid.getHeight(level, block)) {
                    // Keep a hit when a later overlapping ship contains air.
                    shipBlockFluid = fluid;
                }
            }
        }
        if (hiddenRetainedFluid) return new Sample(Fluids.EMPTY.defaultFluidState(), false, false);
        if (dryDomain) return new Sample(Fluids.EMPTY.defaultFluidState(), false, true);
        if (shipBlockFluid != null) return new Sample(shipBlockFluid, true, false);
        return new Sample(vanilla, false, false);
    }

    public record Sample(FluidState fluid, boolean submergedInShip, boolean dryDomain) {
    }

    /** Camera-only grace, measured in game ticks rather than the number of render queries. */
    public static final class Grace {
        private WeakReference<ClientLevel> level = new WeakReference<>(null);
        private long lastTick = Long.MIN_VALUE;
        private long lastDryTick = Long.MIN_VALUE;

        public boolean suppress(final ClientLevel currentLevel, final boolean dry, final int graceTicks) {
            final long tick = currentLevel.getGameTime();
            if (level.get() != currentLevel || tick < lastTick) {
                clear();
                level = new WeakReference<>(currentLevel);
            }
            lastTick = tick;
            if (dry) lastDryTick = tick;
            return graceTicks > 0 && lastDryTick != Long.MIN_VALUE
                && tick - lastDryTick >= 0 && tick - lastDryTick < graceTicks;
        }

        public void clear() {
            level.clear();
            lastTick = Long.MIN_VALUE;
            lastDryTick = Long.MIN_VALUE;
        }
    }
}
