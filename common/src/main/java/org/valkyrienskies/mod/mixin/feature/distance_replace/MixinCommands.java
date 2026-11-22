package org.valkyrienskies.mod.mixin.feature.distance_replace;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;

@Mixin(Commands.class)
public class MixinCommands {

    @WrapOperation(
        method = "performCommand",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/ParseResults;)I",
            remap = false
        )
    )
    private int vs$setDistanceContextLevel(final CommandDispatcher<CommandSourceStack> dispatcher,
        final ParseResults<CommandSourceStack> parse, final Operation<Integer> original) {
        return ShipAwareDistance.withLevel(parse.getContext().getSource().getLevel(),
            () -> original.call(dispatcher, parse));
    }
}
