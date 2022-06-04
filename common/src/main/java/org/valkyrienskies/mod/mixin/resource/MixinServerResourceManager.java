package org.valkyrienskies.mod.mixin.resource;

import net.minecraft.resource.ServerResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.event.RegistryEvents;

@Mixin(ServerResourceManager.class)
public class MixinServerResourceManager {

    @Inject(
        method = "loadRegistryTags",
        at = @At("RETURN")
    )
    void afterTagsLoaded(final CallbackInfo ci) {
        RegistryEvents.tagsAreLoaded();
    }

}
