package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;

public final class ShipSectionMesh implements AutoCloseable {

    public static final RenderType[] CHUNK_LAYERS = new RenderType[] {
        RenderType.solid(),
        RenderType.cutoutMipped(),
        RenderType.cutout(),
        RenderType.translucent(),
        RenderType.tripwire()
    };

    public static int layerIndex(final RenderType renderType) {
        for (int i = 0; i < CHUNK_LAYERS.length; i++) {
            if (CHUNK_LAYERS[i] == renderType) {
                return i;
            }
        }
        return -1;
    }

    public final int originX;
    public final int originY;
    public final int originZ;

    private final VertexBuffer[] buffers = new VertexBuffer[CHUNK_LAYERS.length];

    public ShipSectionMesh(final int originX, final int originY, final int originZ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public void setBuffer(final int layerIndex, final VertexBuffer buffer) {
        final VertexBuffer old = buffers[layerIndex];
        if (old != null) {
            old.close();
        }
        buffers[layerIndex] = buffer;
    }

    public VertexBuffer getBuffer(final int layerIndex) {
        return buffers[layerIndex];
    }

    public boolean isEmpty() {
        for (final VertexBuffer b : buffers) {
            if (b != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null) {
                buffers[i].close();
                buffers[i] = null;
            }
        }
    }
}
