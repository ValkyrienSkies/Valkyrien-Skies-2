package org.valkyrienskies.mod.mixin.server.command;

import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public class MixinCommands {

    @Inject(
        at = @At("TAIL"),
        method = "<init>"
    )
    public void onInit(
        final CommandSelection commandSelection, final CommandBuildContext commandBuildContext, final CallbackInfo ci) {

    }
}
