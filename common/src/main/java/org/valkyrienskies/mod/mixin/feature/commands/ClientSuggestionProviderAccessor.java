package org.valkyrienskies.mod.mixin.feature.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;


@Mixin(ClientSuggestionProvider.class)
public interface ClientSuggestionProviderAccessor {
    @Accessor
    Minecraft getMinecraft();
}
