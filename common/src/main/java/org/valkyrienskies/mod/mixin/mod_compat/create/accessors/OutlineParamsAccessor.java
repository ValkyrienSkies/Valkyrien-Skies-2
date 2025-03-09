package org.valkyrienskies.mod.mixin.mod_compat.create.accessors;

import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.BindableTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Outline.OutlineParams.class)
public interface OutlineParamsAccessor {
    @Accessor("alpha")
    float getAlpha();

    @Accessor("alpha")
    void setAlpha(float alpha);

    @Accessor
    boolean getDisableCull();

    @Accessor
    BindableTexture getFaceTexture();
}
