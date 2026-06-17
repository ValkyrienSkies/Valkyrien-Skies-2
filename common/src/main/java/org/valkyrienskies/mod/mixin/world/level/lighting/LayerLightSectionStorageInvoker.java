package org.valkyrienskies.mod.mixin.world.level.lighting;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageInvoker {

    @Invoker("swapSectionMap")
    void vs$invokeSwapSectionMap();
}
