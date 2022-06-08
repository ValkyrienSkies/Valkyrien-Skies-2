package org.valkyrienskies.mod.mixin.resource;

import net.minecraft.server.ServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.event.RegistryEvents;

@Mixin(ServerResources.class)
public class MixinServerResources {

    @Inject(
        method = "updateGlobals",
        at = @At("RETURN")
    )
    void afterTagsLoaded(final CallbackInfo ci) {
        RegistryEvents.tagsAreLoaded();
    }

}
