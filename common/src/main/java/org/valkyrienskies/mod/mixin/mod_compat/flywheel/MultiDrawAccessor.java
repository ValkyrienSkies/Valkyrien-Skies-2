package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup$MultiDraw", remap = false)
public interface MultiDrawAccessor {
    @Accessor int getStart();
    @Accessor int getEnd();
    @Accessor boolean isEmbedded();
}
