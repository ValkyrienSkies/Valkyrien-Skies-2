package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodingSnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.core.internal.world.VsiShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;

/**
 * Shared client/server view of the fluid occupying a ship topology domain.
 *
 * <p>Flood snapshots do not currently export a velocity field. Entity interaction therefore
 * supplies immersion and vanilla fluid behavior without adding an artificial current.</p>
 */
public final class ShipFluidInteraction {
    private static final PointSample OUTSIDE = new PointSample(false, null);
    private static final Map<VsiShipWorld, Long2ObjectOpenHashMap<CachedBody>> CACHES =
        new WeakHashMap<>();

    private ShipFluidInteraction() {
    }

    public static PointSample samplePoint(final Level level, final Vec3 worldPosition) {
        final VsiShipWorld shipWorld = shipWorld(level);
        if (shipWorld == null) return OUTSIDE;

        final AABBd queryBounds = new AABBd(
            worldPosition.x - 0.001,
            worldPosition.y - 0.001,
            worldPosition.z - 0.001,
            worldPosition.x + 0.001,
            worldPosition.y + 0.001,
            worldPosition.z + 0.001
        );
        final Vector3d local = new Vector3d();
        PointSample drySample = OUTSIDE;
        for (final Ship ship : shipWorld.getAllShips().getIntersecting(
            queryBounds, VSGameUtilsKt.getDimensionId(level)
        )) {
            final CachedBody body = bodyFor(shipWorld, ship.getId());
            if (body == null) continue;

            ship.getWorldToShip().transformPosition(
                worldPosition.x, worldPosition.y, worldPosition.z, local
            );
            final LocalPointSample localSample = body.sampleLocal(local.x, local.y, local.z);
            if (!localSample.insideDomain()) continue;

            final FlowingFluid fluid = localSample.fluidId() == null
                ? null
                : MassDatapackResolver.INSTANCE.getFlowingFluid(localSample.fluidId());
            if (fluid != null) {
                return new PointSample(true, fluid);
            }
            drySample = new PointSample(true, null);
        }
        return drySample;
    }

    public static VolumeSample sampleVolume(
        final Level level,
        final AABB worldBounds,
        final TagKey<Fluid> fluidTag
    ) {
        final VsiShipWorld shipWorld = shipWorld(level);
        if (shipWorld == null) return VolumeSample.UNCONTROLLED;

        final AABBd worldQuery = new AABBd(
            worldBounds.minX,
            worldBounds.minY,
            worldBounds.minZ,
            worldBounds.maxX,
            worldBounds.maxY,
            worldBounds.maxZ
        );
        final Vec3 referencePoint = new Vec3(
            (worldBounds.minX + worldBounds.maxX) * 0.5,
            (worldBounds.minY + worldBounds.maxY) * 0.5,
            (worldBounds.minZ + worldBounds.maxZ) * 0.5
        );
        boolean controlsVanilla = false;
        double maximumDepth = 0.0;
        final Vector3d localReference = new Vector3d();
        final AABBd localBounds = new AABBd();
        final AABBd localFluidBounds = new AABBd();
        final AABBd worldFluidBounds = new AABBd();

        for (final Ship ship : shipWorld.getAllShips().getIntersecting(
            worldQuery, VSGameUtilsKt.getDimensionId(level)
        )) {
            final CachedBody body = bodyFor(shipWorld, ship.getId());
            if (body == null) continue;

            ship.getWorldToShip().transformPosition(
                referencePoint.x, referencePoint.y, referencePoint.z, localReference
            );
            controlsVanilla |= body.sampleLocal(
                localReference.x, localReference.y, localReference.z
            ).insideDomain();

            worldQuery.transform(ship.getWorldToShip(), localBounds);
            final int minX = floorToInt(localBounds.minX);
            final int minY = floorToInt(localBounds.minY);
            final int minZ = floorToInt(localBounds.minZ);
            final int maxX = floorToInt(Math.nextDown(localBounds.maxX));
            final int maxY = floorToInt(Math.nextDown(localBounds.maxY));
            final int maxZ = floorToInt(Math.nextDown(localBounds.maxZ));

            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        final VsiFluidFloodedVoxel voxel = body.getFloodedVoxel(x, y, z);
                        if (voxel == null || voxel.getFillAmount() <= 0) continue;

                        final FlowingFluid fluid =
                            MassDatapackResolver.INSTANCE.getFlowingFluid(voxel.getFluidId());
                        if (fluid == null || !fluid.defaultFluidState().is(fluidTag)) continue;

                        final double fillHeight = Math.min(255, voxel.getFillAmount()) / 255.0;
                        localFluidBounds.minX = x;
                        localFluidBounds.minY = y;
                        localFluidBounds.minZ = z;
                        localFluidBounds.maxX = x + 1.0;
                        localFluidBounds.maxY = y + fillHeight;
                        localFluidBounds.maxZ = z + 1.0;
                        localFluidBounds.transform(ship.getShipToWorld(), worldFluidBounds);
                        if (!intersects(worldBounds, worldFluidBounds)) continue;

                        controlsVanilla = true;
                        maximumDepth = Math.max(
                            maximumDepth,
                            Math.min(worldBounds.maxY, worldFluidBounds.maxY) - worldBounds.minY
                        );
                    }
                }
            }
        }

        return controlsVanilla
            ? new VolumeSample(true, Math.max(0.0, maximumDepth))
            : VolumeSample.UNCONTROLLED;
    }

    private static boolean intersects(final AABB minecraft, final AABBd joml) {
        return minecraft.maxX > joml.minX && minecraft.minX < joml.maxX
            && minecraft.maxY > joml.minY && minecraft.minY < joml.maxY
            && minecraft.maxZ > joml.minZ && minecraft.minZ < joml.maxZ;
    }

    private static @Nullable VsiShipWorld shipWorld(final Level level) {
        final Object shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        return shipWorld instanceof VsiShipWorld ? (VsiShipWorld) shipWorld : null;
    }

    private static synchronized @Nullable CachedBody bodyFor(
        final VsiShipWorld shipWorld,
        final long bodyId
    ) {
        final VsiFluidTopologySnapshot topology;
        final VsiFluidFloodingSnapshot flooding;
        if (shipWorld instanceof final VsiClientShipWorld clientWorld) {
            topology = clientWorld.getFluidTopologySnapshot(bodyId);
            flooding = clientWorld.getFluidFloodingSnapshot(bodyId);
        } else if (shipWorld instanceof final VsiServerShipWorld serverWorld) {
            topology = serverWorld.getFluidTopologySnapshot(bodyId);
            flooding = serverWorld.getFluidFloodingSnapshot(bodyId);
        } else {
            return null;
        }

        final Long2ObjectOpenHashMap<CachedBody> worldCache =
            CACHES.computeIfAbsent(shipWorld, ignored -> new Long2ObjectOpenHashMap<>());
        if (topology == null || !topology.getEnabled() || !topology.getValid()) {
            worldCache.remove(bodyId);
            return null;
        }

        final long floodingSequence =
            flooding == null ? Long.MIN_VALUE : flooding.getStreamSequence();
        final CachedBody cached = worldCache.get(bodyId);
        if (cached != null
            && cached.topologySequence == topology.getStreamSequence()
            && cached.floodingSequence == floodingSequence) {
            return cached;
        }

        final CachedBody rebuilt = new CachedBody(topology, flooding);
        worldCache.put(bodyId, rebuilt);
        return rebuilt;
    }

    static int floorToInt(final double value) {
        final int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    public record PointSample(boolean insideDomain, @Nullable FlowingFluid fluid) {
        public boolean flooded() {
            return fluid != null;
        }
    }

    public record VolumeSample(boolean controlsVanilla, double fluidDepth) {
        private static final VolumeSample UNCONTROLLED = new VolumeSample(false, 0.0);

        public boolean submerged() {
            return fluidDepth > 0.0;
        }
    }

    record LocalPointSample(boolean insideDomain, @Nullable Integer fluidId) {
    }

    static final class CachedBody {
        private final long topologySequence;
        private final long floodingSequence;
        private final Long2ObjectOpenHashMap<VsiFluidTopologyVoxel> topologyByPosition;
        private final Long2ObjectOpenHashMap<VsiFluidFloodedVoxel> floodingByPosition;

        CachedBody(
            final VsiFluidTopologySnapshot topology,
            final @Nullable VsiFluidFloodingSnapshot flooding
        ) {
            topologySequence = topology.getStreamSequence();
            floodingSequence =
                flooding == null ? Long.MIN_VALUE : flooding.getStreamSequence();
            topologyByPosition = new Long2ObjectOpenHashMap<>(topology.getVoxels().size());
            floodingByPosition = new Long2ObjectOpenHashMap<>(
                flooding == null ? 0 : flooding.getVoxels().size()
            );

            for (final VsiFluidTopologyVoxel voxel : topology.getVoxels()) {
                topologyByPosition.put(
                    FloodedFluidClientCache.pack(
                        voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()
                    ),
                    voxel
                );
            }
            if (flooding != null) {
                for (final VsiFluidFloodedVoxel voxel : flooding.getVoxels()) {
                    if (voxel.getFillAmount() <= 0) continue;
                    floodingByPosition.put(
                        FloodedFluidClientCache.pack(
                            voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()
                        ),
                        voxel
                    );
                }
            }
        }

        LocalPointSample sampleLocal(final double x, final double y, final double z) {
            final int voxelX = floorToInt(x);
            final int voxelY = floorToInt(y);
            final int voxelZ = floorToInt(z);
            final long packed = FloodedFluidClientCache.pack(voxelX, voxelY, voxelZ);
            if (!topologyByPosition.containsKey(packed)) {
                return new LocalPointSample(false, null);
            }

            final VsiFluidFloodedVoxel flooded = floodingByPosition.get(packed);
            if (flooded != null && FloodedFluidClientCache.isLocallySubmerged(
                flooded.getFillAmount(), y - voxelY
            )) {
                return new LocalPointSample(true, flooded.getFluidId());
            }
            return new LocalPointSample(true, null);
        }

        @Nullable VsiFluidFloodedVoxel getFloodedVoxel(
            final int x,
            final int y,
            final int z
        ) {
            return floodingByPosition.get(FloodedFluidClientCache.pack(x, y, z));
        }
    }
}
