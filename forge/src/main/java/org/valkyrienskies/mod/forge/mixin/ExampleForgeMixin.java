package org.valkyrienskies.mod.forge.mixin;

import net.minecraft.client.gui.screen.TitleScreen;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TitleScreen.class)
public class ExampleForgeMixin {

    @Shadow
    @Final
    private static Logger LOGGER;

    /*
    @Inject(
        at = @At("HEAD"),
        method = "init"
    )
    public void inject(final CallbackInfo info) {
        LOGGER.info("Hello from ExampleForgeMixin");
    }
     */

}
