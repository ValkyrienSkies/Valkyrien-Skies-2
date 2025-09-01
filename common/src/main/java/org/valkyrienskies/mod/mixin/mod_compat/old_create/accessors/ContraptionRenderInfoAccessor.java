package org.valkyrienskies.mod.mixin.mod_compat.old_create.accessors;

import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.simibubi.create.content.contraptions.render.ContraptionRenderInfo")
public interface ContraptionRenderInfoAccessor {
    @Accessor("contraption")
    Contraption getContraption();
}
