package org.valkyrienskies.mod.mixinducks.mod_compat.create;

import com.simibubi.create.content.contraptions.StructureTransform;

public interface MixinAbstractContraptionEntityDuck {
    void vs$setForceStall(boolean forceStall);

    StructureTransform getStructureTransform();
}
