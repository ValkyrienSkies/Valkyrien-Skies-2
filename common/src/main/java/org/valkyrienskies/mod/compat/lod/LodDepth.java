package org.valkyrienskies.mod.compat.lod;

public final class LodDepth {
    public int textureId;
    public float projectionA;
    public float projectionB;
    public float ndcScale = 2.0f;
    public float ndcBias = -1.0f;
    public float emptyDepth = 1.0f;

    public boolean setProjection(final float m22, final float m23, final float m32) {
        final float b;
        if (Math.abs(m32 + 1.0f) < 1.0e-3f) {
            b = m23;
        } else if (Math.abs(m23 + 1.0f) < 1.0e-3f) {
            b = m32;
        } else {
            return false;
        }
        if (!Float.isFinite(m22) || !Float.isFinite(b) || b == 0.0f) {
            return false;
        }
        projectionA = m22;
        projectionB = b;
        return true;
    }
}
