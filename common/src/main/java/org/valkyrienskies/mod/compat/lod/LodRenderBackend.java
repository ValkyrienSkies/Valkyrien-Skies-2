package org.valkyrienskies.mod.compat.lod;

public interface LodRenderBackend {

    String name();

    int renderDistanceBlocks();

    boolean depthForThisFrame(LodDepth out);

    float overpaintDistanceBlocks(float partialTick);

    boolean fogFractions(float[] out);
}
