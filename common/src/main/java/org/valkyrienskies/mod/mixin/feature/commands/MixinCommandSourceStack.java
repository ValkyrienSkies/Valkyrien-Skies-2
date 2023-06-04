package org.valkyrienskies.mod.mixin.feature.commands;

import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.apigame.world.ShipWorldCore;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource;

@Mixin(CommandSourceStack.class)
public abstract class MixinCommandSourceStack implements VSCommandSource {
    @Shadow
    @Final
    private CommandSource source;

    @Shadow
    public abstract ServerLevel getLevel();

    @NotNull
    @Override
    public ShipWorldCore getShipWorld() {
        return VSGameUtilsKt.getShipObjectWorld(getLevel());
    }

    @Override
    public void sendVSMessage(final Component component, final UUID uUID) {
        source.sendSystemMessage(component);
    }
}
