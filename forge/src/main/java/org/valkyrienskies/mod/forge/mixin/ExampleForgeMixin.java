package org.valkyrienskies.mod.forge.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class ExampleForgeMixin {

	@Shadow @Final private static Logger LOGGER;

	@Inject(
		at = @At("HEAD"),
		method = "init"
	)
	public void inject(CallbackInfo info) {
		LOGGER.info("Hello from ExampleForgeMixin");
	}

}
