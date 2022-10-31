package org.valkyrienskies.mod.mixin.feature.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.world.ShipWorldCore;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource;

@Mixin(CommandSourceStack.class)
public abstract class MixinCommandSourceStack implements VSCommandSource {

    @Shadow
    public abstract ServerLevel getLevel();

    @NotNull
    @Override
    public ShipWorldCore getShipWorld() {
        return VSGameUtilsKt.getShipObjectWorld(getLevel());
    }
}
