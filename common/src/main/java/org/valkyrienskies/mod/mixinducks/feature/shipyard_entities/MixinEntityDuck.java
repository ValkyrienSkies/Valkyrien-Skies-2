package org.valkyrienskies.mod.mixinducks.feature.shipyard_entities;

import org.valkyrienskies.mod.common.entity.handling.VSEntityHandler;

public interface MixinEntityDuck {
    public VSEntityHandler vs_getCustomHandler();

    public void vs_setCustomHandler(VSEntityHandler handler);
}
