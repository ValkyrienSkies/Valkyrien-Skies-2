package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.VertexConsumer;

final class OffsetVertexConsumer implements VertexConsumer {
    private VertexConsumer delegate;
    private double dx;
    private double dy;
    private double dz;

    OffsetVertexConsumer set(final VertexConsumer delegate, final double dx, final double dy, final double dz) {
        this.delegate = delegate;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        return this;
    }

    @Override
    public VertexConsumer addVertex(final float x, final float y, final float z) {
        // Attribute setters can bypass this wrapper: only positions need an offset.
        return delegate.addVertex((float) (x + dx), (float) (y + dy), (float) (z + dz));
    }

    @Override
    public void addVertex(final float x, final float y, final float z, final int color,
        final float u, final float v, final int overlay, final int light,
        final float normalX, final float normalY, final float normalZ) {
        delegate.addVertex((float) (x + dx), (float) (y + dy), (float) (z + dz), color,
            u, v, overlay, light, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
        return delegate.setColor(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer setUv(final float u, final float v) {
        return delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(final int u, final int v) {
        return delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(final int u, final int v) {
        return delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(final float x, final float y, final float z) {
        return delegate.setNormal(x, y, z);
    }
}
