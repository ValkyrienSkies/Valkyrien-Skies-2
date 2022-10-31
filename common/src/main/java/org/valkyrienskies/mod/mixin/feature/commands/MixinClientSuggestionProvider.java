package org.valkyrienskies.mod.mixin.feature.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.commands.VSCommandSource;
import org.valkyrienskies.core.game.ships.ShipObjectWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ClientSuggestionProvider.class)
public class MixinClientSuggestionProvider implements VSCommandSource {
    @Shadow
    @Final
    private Minecraft minecraft;

    @NotNull
    @Override
    public ShipObjectWorld<?> getShipWorld() {
        return VSGameUtilsKt.getShipObjectWorld(this.minecraft.level);
    }

    @Nullable
    @Override
    public Vector3d getCalledAt() {
        return VectorConversionsMCKt.toJOML(minecraft.player.position());
    }
}
