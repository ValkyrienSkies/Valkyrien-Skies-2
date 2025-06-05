package org.valkyrienskies.mod.fabric.mixin.compat.create.accessors;

import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.foundation.outliner.Outline;
import java.util.Optional;
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
    Optional<AllSpecialTextures> getFaceTexture();
}
