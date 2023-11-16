package org.valkyrienskies.mod.mixin.feature.commands;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.world.ShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource;

@Mixin(ClientSuggestionProvider.class)
public class MixinClientSuggestionProvider implements VSCommandSource {
    @Shadow
    @Final
    private Minecraft minecraft;

    @NotNull
    @Override
    public ShipWorld getShipWorld() {
        assert this.minecraft.level != null;
        return VSGameUtilsKt.getShipObjectWorld(this.minecraft.level);
    }

    @Override
    public void sendVSMessage(final Component component, final UUID uUID) {
        minecraft.player.sendSystemMessage(component);
    }
}
