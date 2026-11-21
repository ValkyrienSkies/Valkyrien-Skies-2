package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.backend.engine.indirect.IndirectDraw;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IndirectDraw.class, remap = false)
public interface IndirectDrawAccessor {
    @Accessor IndirectInstancer<?> getInstancer();
}
