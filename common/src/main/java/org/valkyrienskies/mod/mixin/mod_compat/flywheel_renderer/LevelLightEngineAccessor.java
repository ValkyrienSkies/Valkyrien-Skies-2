package org.valkyrienskies.mod.mixin.mod_compat.flywheel_renderer;

import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelLightEngine.class)
public interface LevelLightEngineAccessor {

    @Accessor("blockEngine")
    void setBlockLightEngine(LightEngine<?,?> blockLightEngine);

    @Accessor("blockEngine")
    LightEngine<?, ?> getBlockLightEngine();
}
