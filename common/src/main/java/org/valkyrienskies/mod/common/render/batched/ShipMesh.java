package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.valkyrienskies.mod.compat.sodium.light.ShipEmitterSelection;

final class ShipMesh implements AutoCloseable {

    final VertexBuffer[] opaque;
    final Long2ObjectMap<ShipSectionMesh> translucentSections;

    final int refX;
    final int refY;
    final int refZ;
    final double[] emitters;
    final int[] selectedEmitters = new int[ShipEmitterSelection.MAX_SELECTED];
    int selectedEmitterCount;

    ShipMesh(final VertexBuffer[] opaque, final Long2ObjectMap<ShipSectionMesh> translucentSections,
        final int refX, final int refY, final int refZ, final double[] emitters) {
        this.opaque = opaque;
        this.translucentSections = translucentSections;
        this.refX = refX;
        this.refY = refY;
        this.refZ = refZ;
        this.emitters = emitters;
        for (final ShipSectionMesh section : translucentSections.values()) section.owner = this;
    }

    VertexBuffer getOpaque(final int layerIndex) {
        return layerIndex >= 0 && layerIndex < opaque.length ? opaque[layerIndex] : null;
    }

    boolean isEmpty() {
        if (!translucentSections.isEmpty()) {
            return false;
        }
        for (final VertexBuffer buffer : opaque) {
            if (buffer != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        for (int i = 0; i < opaque.length; i++) {
            if (opaque[i] != null) {
                opaque[i].close();
                opaque[i] = null;
            }
        }
        for (final ShipSectionMesh mesh : translucentSections.values()) {
            mesh.close();
        }
        translucentSections.clear();
    }
}
