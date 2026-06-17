package org.valkyrienskies.mod.mixin.server.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.command.commands.VanillaExecuteCommand;
import org.valkyrienskies.mod.common.command.commands.VanillaTeleportCommand;

@Mixin(Commands.class)
public class MixinCommands {
    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(
        at = @At("TAIL"),
        method = "<init>"
    )
    public void onInit(CommandSelection commandSelection, CommandBuildContext commandBuildContext, CallbackInfo ci) {
        VanillaTeleportCommand.register(this.dispatcher);
        VanillaExecuteCommand.register(this.dispatcher);
    }
}
