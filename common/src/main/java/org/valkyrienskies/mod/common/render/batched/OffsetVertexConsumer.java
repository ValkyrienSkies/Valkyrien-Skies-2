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
    public VertexConsumer vertex(final double x, final double y, final double z) {
        // Returns the delegate so the chained color()/uv()/... calls (which need no offset) skip this wrapper.
        return delegate.vertex(x + dx, y + dy, z + dz);
    }

    @Override
    public void vertex(final float x, final float y, final float z, final float red, final float green,
        final float blue, final float alpha, final float texU, final float texV, final int overlayUV,
        final int lightmapUV, final float normalX, final float normalY, final float normalZ) {
        delegate.vertex((float) (x + dx), (float) (y + dy), (float) (z + dz), red, green, blue, alpha,
            texU, texV, overlayUV, lightmapUV, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer color(final int red, final int green, final int blue, final int alpha) {
        return delegate.color(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer uv(final float u, final float v) {
        return delegate.uv(u, v);
    }

    @Override
    public VertexConsumer overlayCoords(final int u, final int v) {
        return delegate.overlayCoords(u, v);
    }

    @Override
    public VertexConsumer uv2(final int u, final int v) {
        return delegate.uv2(u, v);
    }

    @Override
    public VertexConsumer normal(final float x, final float y, final float z) {
        return delegate.normal(x, y, z);
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(final int red, final int green, final int blue, final int alpha) {
        delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
