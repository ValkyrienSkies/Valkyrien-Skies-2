package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.world.VsiShipWorld;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;
import org.valkyrienskies.mod.common.pathfinding.NearbyShip;
import org.valkyrienskies.mod.common.pathfinding.PathPerFrameRegistry;
import org.valkyrienskies.mod.common.pathfinding.PathfindStateCache;
import org.valkyrienskies.mod.common.pathfinding.PathTypeDanger;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;
import org.valkyrienskies.mod.util.FluidStateManager;

/** Read-only block-cell views. Unlike eye samples, a cell reports even a shallow retained layer. */
public final class ShipFluidStateQueries {
    private ShipFluidStateQueries() { }

    private static final ThreadLocal<Query> PATH_QUERY = new ThreadLocal<>();

    /** A non-null result owns this query, including an explicitly dry cell. */
    public record Cell(FluidState fluid, @Nullable BlockState obstruction) {
        public BlockState blockState(final BlockState original) {
            final BlockState state = obstruction != null ? obstruction : original;
            if (state.blocksMotion()) return state;
            // WalkNodeEvaluator checks isAir before FluidState. Represent only air/liquid
            // cells virtually; do not replace plants, fire or other pathfinding hazards.
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                return fluid.isEmpty() ? Blocks.AIR.defaultBlockState() : fluid.createLegacyBlock();
            }
            if (fluid.isEmpty() && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                return state.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            return state;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Query previous;
        private boolean closed;
        private Scope(final Query query) {
            previous = PATH_QUERY.get();
            PATH_QUERY.set(query);
            PathfindStateCache.reset();
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) PATH_QUERY.remove(); else PATH_QUERY.set(previous);
            PathfindStateCache.reset();
        }
    }

    public static Scope beginPath(final Level level, final PathfindingFrame frame, final NearbyShip[] ships) {
        return new Scope(new Query(level, shipWorld(level), frame, ships));
    }

    public static @Nullable Cell forPath(final Level level, final BlockPos pos) {
        final Query current = PATH_QUERY.get();
        if (current != null && current.level == level) return current.resolve(pos);
        final PathfindingFrame frame = PathfindingFrame.current(null);
        if (frame == null) return at(level, pos);
        return new Query(level, shipWorld(level), frame, PathPerFrameRegistry.getNearbyShips()).resolve(pos);
    }

    /** Shipyard positions stay local; ordinary positions query the ships at that world cell. */
    public static @Nullable Cell at(final Level level, final BlockPos pos) {
        if (shipWorld(level) == null) return null;
        final Ship owner = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (owner != null) return local(level, owner, pos);
        return atWorld(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static @Nullable Cell local(final Level level, final Ship ship, final BlockPos pos) {
        final VsiShipWorld world = shipWorld(level);
        // A live solid may be newer than the snapshot; preserve its real (possibly waterlogged) state.
        return world == null || level.getBlockState(pos).blocksMotion()
            ? null : localCell(ShipFluidInteraction.bodyFor(world, ship.getId()), pos, world instanceof VsiClientShipWorld);
    }

    public static @Nullable Cell atWorld(final Level level, final double x, final double y, final double z) {
        final VsiShipWorld world = shipWorld(level);
        if (world == null) return null;
        final List<NearbyShip> ships = new ArrayList<>();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level,
            new AABBd(Math.floor(x), Math.floor(y), Math.floor(z), Math.floor(x) + 1,
                Math.floor(y) + 1, Math.floor(z) + 1))) {
            ships.add(new NearbyShip(ship, new AABBd(ship.getWorldAABB()), new Matrix4d(ship.getWorldToShip())));
        }
        return new Query(level, world, null, ships.toArray(NearbyShip[]::new)).world(x, y, z);
    }

    public static FluidState getFluidState(final Level level, final BlockPos pos,
        final @Nullable FluidStateManager.QueryCache cache) {
        final Cell override = at(level, pos);
        return override != null ? override.fluid() : FluidStateManager.getFluidState(level, pos, cache);
    }

    private static @Nullable VsiShipWorld shipWorld(final Level level) {
        final Object world = VSGameUtilsKt.getShipWorldNullable(level);
        return world instanceof VsiShipWorld ? (VsiShipWorld) world : null;
    }

    private static @Nullable Cell localCell(final @Nullable ShipFluidInteraction.CachedBody body, final BlockPos pos,
        final boolean clientSide) {
        if (body == null) return null;
        // Sample the cell's bottom, not its center: a one-unit fill still occupies this cell.
        final var sample = body.sampleLocal(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (!sample.insideDomain()) return null;
        if (sample.fluidId() == null) return new Cell(Fluids.EMPTY.defaultFluidState(), null);
        final FlowingFluid fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(sample.fluidId(), clientSide);
        if (fluid == null) return null; // Unavailable registered fluid is not proof of dry air.
        final var voxel = body.getFloodedVoxel(pos.getX(), pos.getY(), pos.getZ());
        final int fill = Math.min(255, voxel.getFillAmount());
        return new Cell(fill == 255 ? fluid.getSource(false) : fluid.getFlowing((fill * 8 + 254) / 255, false), null);
    }

    /** One path's owning snapshots and transforms, also usable by direct regression tests. */
    static final class Query {
        private final Level level;
        private final boolean clientSide;
        private final PathfindingFrame frame;
        private final NearbyShip[] ships;
        private final Long2ObjectOpenHashMap<ShipFluidInteraction.CachedBody> bodies = new Long2ObjectOpenHashMap<>();

        Query(final Level level, final @Nullable VsiShipWorld world, final @Nullable PathfindingFrame frame,
            final NearbyShip[] ships) {
            this.level = level;
            this.clientSide = world instanceof VsiClientShipWorld;
            this.frame = frame instanceof PathfindingFrame.InShip inShip
                ? new PathfindingFrame.InShip(inShip.getShip(), inShip.getShipId(), new Matrix4d(inShip.getShipToWorldSnapshot()))
                : frame;
            this.ships = new NearbyShip[ships.length];
            for (int i = 0; i < ships.length; ++i) {
                final NearbyShip ship = ships[i];
                this.ships[i] = new NearbyShip(ship.ship, new AABBd(ship.worldAABB), new Matrix4d(ship.worldToShip));
                capture(world, ship.ship);
            }
            if (frame instanceof PathfindingFrame.InShip inShip) capture(world, inShip.getShip());
        }

        private void capture(final @Nullable VsiShipWorld world, final Ship ship) {
            if (world != null && !bodies.containsKey(ship.getId())) {
                bodies.put(ship.getId(), ShipFluidInteraction.bodyFor(world, ship.getId()));
            }
        }

        @Nullable Cell resolve(final BlockPos pos) {
            if (frame instanceof PathfindingFrame.InShip inShip) {
                final BlockState raw = level.getBlockState(pos);
                final Cell cell = localCell(bodies.get(inShip.getShipId()), pos, clientSide);
                if (cell != null) return raw.blocksMotion() ? new Cell(Fluids.EMPTY.defaultFluidState(), raw) : cell;
                if (inShip.getShip().getChunkClaim().contains(pos.getX() >> 4, pos.getZ() >> 4)
                    && (!raw.getFluidState().isEmpty() || raw.blocksMotion())) return null;
                final Vector3d world = inShip.getShipToWorldSnapshot().transformPosition(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new Vector3d());
                return world(world.x, world.y, world.z);
            }
            return world(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }

        @Nullable Cell world(final double x, final double y, final double z) {
            final BlockPos worldPos = BlockPos.containing(x, y, z);
            final BlockState worldState = level.getBlockState(worldPos);
            if (worldState.blocksMotion()) return null;
            Cell wet = null;
            boolean controlled = false;
            List<BlockPos> projectedBlocks = null;
            final Vector3d local = new Vector3d();
            for (final NearbyShip ship : ships) {
                if (!ship.worldAABB.containsPoint(x, y, z)) continue;
                ship.worldToShip.transformPosition(x, y, z, local);
                final BlockPos pos = BlockPos.containing(local.x, local.y, local.z);
                final BlockState raw = level.getBlockState(pos);
                if (raw.blocksMotion()) return new Cell(Fluids.EMPTY.defaultFluidState(), raw);
                if (!raw.isAir() && !(raw.getBlock() instanceof LiquidBlock)) {
                    if (projectedBlocks == null) projectedBlocks = new ArrayList<>(2);
                    projectedBlocks.add(pos);
                }
                final Cell cell = localCell(bodies.get(ship.ship.getId()), pos, clientSide);
                controlled |= cell != null;
                final FluidState candidate = cell != null ? cell.fluid() : raw.getFluidState();
                if (!candidate.isEmpty() && (wet == null || candidate.is(FluidTags.LAVA)
                    || candidate.getType().isSame(Fluids.LAVA))) wet = new Cell(candidate, null);
            }
            if (controlled) {
                // Keep the existing ship hazard/plant overlay while changing only its fluid view.
                final BlockState block = projectedBlocks == null ? null : projectedBlocks.size() == 1
                    ? level.getBlockState(projectedBlocks.get(0))
                    : PathTypeDanger.pickMostDangerousState(level, projectedBlocks);
                return new Cell(wet != null ? wet.fluid() : Fluids.EMPTY.defaultFluidState(), block);
            }
            // Preserve ordinary ambient water before falling back to real ship-block water.
            return worldState.getFluidState().isEmpty() ? wet : null;
        }
    }
}
