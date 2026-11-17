package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

final class ShipMesh implements AutoCloseable {

    /** One buffer per merged layer, null for sorted and empty layers. */
    final VertexBuffer[] merged;
    /** Sections with geometry in a sorted layer, drawn per section in back-to-front order. */
    final Long2ObjectMap<ShipSectionMesh> sortedSections;

    final int refX;
    final int refY;
    final int refZ;

    ShipMesh(final VertexBuffer[] merged, final Long2ObjectMap<ShipSectionMesh> sortedSections,
        final int refX, final int refY, final int refZ) {
        this.merged = merged;
        this.sortedSections = sortedSections;
        this.refX = refX;
        this.refY = refY;
        this.refZ = refZ;
    }

    /**
     * Merge the changed layers and collect the sections with sorted layers ({@link ShipSectionMesh#isSorted}) into
     * a new mesh, then close {@code old}. Unchanged merged layers move from {@code old} to the new mesh.
     *
     * @return the new mesh, or null when there are no sections
     */
    static ShipMesh rebuild(final ShipMesh old,
        final Long2ObjectMap<ShipSectionCompiler.CompiledSection> sections, final boolean[] changedLayers,
        final ShipSectionCompiler compiler, final int refX, final int refY, final int refZ) {
        if (sections.isEmpty()) {
            if (old != null) {
                old.close();
            }
            return null;
        }

        final VertexBuffer[] merged = new VertexBuffer[ShipSectionCompiler.LAYER_COUNT];
        for (int i = 0; i < merged.length; i++) {
            if (ShipSectionMesh.isSorted(i)) {
                continue;
            }
            if (old == null || changedLayers[i]) {
                merged[i] = compiler.mergeLayer(i, sections.values());
            } else {
                // Unchanged layer: move the buffer to the new mesh so closing the old one keeps it.
                merged[i] = old.merged[i];
                old.merged[i] = null;
            }
        }

        final Long2ObjectMap<ShipSectionMesh> sorted = new Long2ObjectOpenHashMap<>();
        for (final var entry : sections.long2ObjectEntrySet()) {
            if (entry.getValue().sorted != null) {
                sorted.put(entry.getLongKey(), entry.getValue().sorted);
            }
        }

        final ShipMesh mesh = new ShipMesh(merged, sorted, refX, refY, refZ);
        if (old != null) {
            old.close();
        }
        return mesh;
    }

    VertexBuffer getMerged(final int layerIndex) {
        return layerIndex >= 0 && layerIndex < merged.length ? merged[layerIndex] : null;
    }

    boolean isEmpty() {
        if (!sortedSections.isEmpty()) {
            return false;
        }
        for (final VertexBuffer buffer : merged) {
            if (buffer != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        for (int i = 0; i < merged.length; i++) {
            if (merged[i] != null) {
                merged[i].close();
                merged[i] = null;
            }
        }
    }
}
