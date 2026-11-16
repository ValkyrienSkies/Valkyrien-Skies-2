package org.valkyrienskies.mod.common.render.batched;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.mod.common.networking.DistantShipTransforms;

/**
 * A render-only copy of a ship beyond the ship load distance, built from a block snapshot the server sent.
 *
 * <p>The server sends a new transform every few ticks while the ship moves. The pose is blended from the pose at the
 * moment an update arrives to the new transform, over the time between updates, so moving ships stay smooth.
 */
final class DistantShipRenderObject extends BatchedShipDrawable {

    final long id;
    final String dimension;
    private int revision;

    private final Long2ObjectOpenHashMap<LevelChunkSection> blocks = new Long2ObjectOpenHashMap<>();
    /** Shipyard block bounds: min x, y, z then max x, y, z, inclusive. */
    private final int[] shipAABB = new int[6];

    // Pose blending. Values use the DistantShipTransforms layout.
    private final double[] from = new double[DistantShipTransforms.SIZE];
    private final double[] to = new double[DistantShipTransforms.SIZE];
    private long blendStartNanos;
    private long blendNanos;
    private long lastUpdateNanos;
    private long poseFrame = Long.MIN_VALUE;

    private final Vector3d position = new Vector3d();
    private final Quaterniond rotation = new Quaterniond();
    private final Quaterniond rotationTo = new Quaterniond();
    private final Vector3d scaling = new Vector3d(1.0);
    private final Vector3d positionInModel = new Vector3d();
    private final Matrix4d shipToWorld = new Matrix4d();
    private final AABBd worldAABB = new AABBd();

    // Mesh, built on the render thread when needed.
    private final Long2ObjectOpenHashMap<ShipSectionCompiler.CompiledSection> sections = new Long2ObjectOpenHashMap<>();
    private final boolean[] changedLayers = new boolean[ShipSectionCompiler.LAYER_COUNT];
    private ShipMesh mesh;
    private boolean meshEmpty = true;
    private boolean meshDirty = true;
    private int refX;
    private int refY;
    private int refZ;

    /** The ship's loaded counterpart is drawing, so this copy is not. */
    boolean suppressed;
    /** The server asked to remove this copy. It stays until the loaded ship draws, or a short while. */
    long removeRequestedNanos = -1;

    DistantShipRenderObject(final long id, final String dimension) {
        this.id = id;
        this.dimension = dimension;
    }

    int revision() {
        return revision;
    }

    /** Replace the blocks. The mesh is rebuilt on the next frame that draws this ship. */
    void setBlocks(final int revision, final int[] shipAABB, final Long2ObjectMap<LevelChunkSection> blocks) {
        this.revision = revision;
        System.arraycopy(shipAABB, 0, this.shipAABB, 0, Math.min(6, shipAABB.length));
        this.blocks.clear();
        this.blocks.putAll(blocks);
        this.meshDirty = true;
    }

    /** Jump to a transform without blending. */
    void setTransform(final double[] transform, final int offset) {
        System.arraycopy(transform, offset, to, 0, DistantShipTransforms.SIZE);
        System.arraycopy(transform, offset, from, 0, DistantShipTransforms.SIZE);
        blendNanos = 0;
        lastUpdateNanos = System.nanoTime();
        poseFrame = Long.MIN_VALUE;
    }

    /** Blend from the current pose to a new transform over about the time since the last update. */
    void blendTo(final double[] transform, final int offset) {
        final long now = System.nanoTime();
        writePose(now, from);
        System.arraycopy(transform, offset, to, 0, DistantShipTransforms.SIZE);
        // The server sends updates every few ticks; use the measured gap, kept to a sane range.
        blendNanos = Math.max(50_000_000L, Math.min(500_000_000L, now - lastUpdateNanos));
        blendStartNanos = now;
        lastUpdateNanos = now;
        poseFrame = Long.MIN_VALUE;
    }

    /** Write the blended pose at {@code nanos} into {@code out}, in the transform layout. */
    private void writePose(final long nanos, final double[] out) {
        final double t = blendFactor(nanos);
        for (int i = 0; i < DistantShipTransforms.SIZE; i++) {
            out[i] = from[i] + (to[i] - from[i]) * t;
        }
        final int r = DistantShipTransforms.ROTATION;
        rotation.set(from[r], from[r + 1], from[r + 2], from[r + 3]);
        rotationTo.set(to[r], to[r + 1], to[r + 2], to[r + 3]);
        rotation.slerp(rotationTo, t).normalize();
        out[r] = rotation.x;
        out[r + 1] = rotation.y;
        out[r + 2] = rotation.z;
        out[r + 3] = rotation.w;
    }

    private double blendFactor(final long nanos) {
        if (blendNanos <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, (double) (nanos - blendStartNanos) / blendNanos));
    }

    /** Compute the pose for this frame, once per frame. */
    void updatePose(final long frame) {
        if (poseFrame == frame) return;
        poseFrame = frame;
        final double t = blendFactor(System.nanoTime());
        final int p = DistantShipTransforms.POSITION;
        final int s = DistantShipTransforms.SCALING;
        final int m = DistantShipTransforms.POSITION_IN_MODEL;
        final int r = DistantShipTransforms.ROTATION;
        position.set(lerp(p, t), lerp(p + 1, t), lerp(p + 2, t));
        scaling.set(lerp(s, t), lerp(s + 1, t), lerp(s + 2, t));
        positionInModel.set(lerp(m, t), lerp(m + 1, t), lerp(m + 2, t));
        rotation.set(from[r], from[r + 1], from[r + 2], from[r + 3]);
        rotationTo.set(to[r], to[r + 1], to[r + 2], to[r + 3]);
        rotation.slerp(rotationTo, t).normalize();

        // Same composition as a ship transform: translate, rotate, scale, then move the shipyard origin.
        shipToWorld.translation(position).rotate(rotation).scale(scaling)
            .translate(-positionInModel.x, -positionInModel.y, -positionInModel.z);
        worldAABB.setMin(shipAABB[0], shipAABB[1], shipAABB[2])
            .setMax(shipAABB[3] + 1.0, shipAABB[4] + 1.0, shipAABB[5] + 1.0)
            .transform(shipToWorld);
    }

    private double lerp(final int index, final double t) {
        return from[index] + (to[index] - from[index]) * t;
    }

    /** Build the mesh if the blocks changed. */
    boolean needsMesh() {
        return meshDirty;
    }

    void buildMesh(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler, final DistantShipBlockView view) {
        meshDirty = false;
        closeSections();
        final LongArrayList positions = new LongArrayList(blocks.keySet());
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int i = 0; i < positions.size(); i++) {
            final long pos = positions.getLong(i);
            minX = Math.min(minX, SectionPos.x(pos));
            minZ = Math.min(minZ, SectionPos.z(pos));
        }
        if (minX != Integer.MAX_VALUE) {
            refX = minX << 4;
            refY = level.getMinBuildHeight();
            refZ = minZ << 4;
        }
        Arrays.fill(changedLayers, true);
        if (!positions.isEmpty()) {
            // Tint with the biome below the ship. Unloaded chunks report the default biome.
            final var biome = level.getBiome(BlockPos.containing(
                (worldAABB.minX() + worldAABB.maxX()) * 0.5,
                (worldAABB.minY() + worldAABB.maxY()) * 0.5,
                (worldAABB.minZ() + worldAABB.maxZ()) * 0.5));
            view.set(level, blocks, biome);
            try {
                compiler.compileSections(level, dispatcher, view, blocks::get, positions,
                    refX, refY, refZ, sections, changedLayers);
            } finally {
                view.clear();
            }
        }
        mesh = ShipMesh.rebuild(mesh, sections, changedLayers, compiler, refX, refY, refZ);
        meshEmpty = mesh == null || mesh.isEmpty();
    }

    /** Free the GPU mesh but keep the blocks, so it can be built again. */
    void freeMesh() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        closeSections();
        meshEmpty = true;
        meshDirty = true;
    }

    private void closeSections() {
        for (final ShipSectionCompiler.CompiledSection section : sections.values()) {
            section.close();
        }
        sections.clear();
    }

    @Override
    long shipId() {
        return id;
    }

    @Override
    ShipMesh getMesh() {
        return mesh;
    }

    @Override
    boolean isEmpty() {
        return meshEmpty;
    }

    @Override
    Vector3dc renderPosition() {
        return position;
    }

    @Override
    Quaterniondc renderRotation() {
        return rotation;
    }

    @Override
    Vector3dc renderScaling() {
        return scaling;
    }

    @Override
    Vector3dc renderPositionInModel() {
        return positionInModel;
    }

    @Override
    Matrix4dc renderShipToWorld() {
        return shipToWorld;
    }

    @Override
    AABBdc renderWorldAABB() {
        return worldAABB;
    }

    @Override
    public void close() {
        freeMesh();
        blocks.clear();
    }
}
