package org.valkyrienskies.mod.mixin.server.command;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
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
    public void onInit(CommandSelection commandSelection, CommandBuildContext commandBuildContext, CallbackInfo ci) {

    }
}
