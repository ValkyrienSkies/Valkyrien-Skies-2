package org.valkyrienskies.mod.compat.lod;

import org.joml.primitives.AABBdc;

@FunctionalInterface
public interface LodOcclusionTest {

    boolean isHidden(AABBdc worldAABB);
}
