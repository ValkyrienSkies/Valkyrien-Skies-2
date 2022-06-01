package org.valkyrienskies.mod.mixin.server;

import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.event.RegistryEvents;

@Mixin(Main.class)
public class MixinMain {

    @Inject(
        method = "main",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resource/ServerResourceManager;loadRegistryTags()V",
            shift = At.Shift.AFTER
        )
    )
    private static void afterTags(final String[] strings, final CallbackInfo ci) {
        RegistryEvents.tagsAreLoaded();
    }

}
