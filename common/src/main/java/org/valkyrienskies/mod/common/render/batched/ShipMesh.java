package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.RenderType;

final class ShipMesh implements AutoCloseable {

    final VertexBuffer[] opaque;
    final Long2ObjectMap<ShipSectionMesh> translucentSections;

    final int refX;
    final int refY;
    final int refZ;

    ShipMesh(final VertexBuffer[] opaque, final Long2ObjectMap<ShipSectionMesh> translucentSections,
        final int refX, final int refY, final int refZ) {
        this.opaque = opaque;
        this.translucentSections = translucentSections;
        this.refX = refX;
        this.refY = refY;
        this.refZ = refZ;
    }

    /**
     * Merge the changed opaque layers and collect the translucent sections into a new mesh, then close
     * {@code old}. Unchanged opaque layers move from {@code old} to the new mesh.
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

        final VertexBuffer[] opaque = new VertexBuffer[ShipSectionCompiler.LAYER_COUNT];
        final int translucentIndex = ShipSectionMesh.layerIndex(RenderType.translucent());
        for (int i = 0; i < opaque.length; i++) {
            if (i == translucentIndex) {
                continue;
            }
            if (old == null || changedLayers[i]) {
                opaque[i] = compiler.mergeOpaqueLayer(i, sections.values());
            } else {
                opaque[i] = old.opaque[i];
                old.opaque[i] = null;
            }
        }

        final Long2ObjectMap<ShipSectionMesh> translucent = new Long2ObjectOpenHashMap<>();
        for (final var entry : sections.long2ObjectEntrySet()) {
            if (entry.getValue().translucent != null) {
                translucent.put(entry.getLongKey(), entry.getValue().translucent);
            }
        }

        final ShipMesh mesh = new ShipMesh(opaque, translucent, refX, refY, refZ);
        if (old != null) {
            old.close();
        }
        return mesh;
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
    }
}
