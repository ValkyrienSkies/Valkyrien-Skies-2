package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.ShipBlockQueries;

// These navigations override isStableDestination with direct level.getBlockState reads (the base-class wrap in
// MixinPathNavigation doesn't reach overrides), so ship water, lava or decks read as world air. When vanilla sees
// air, use the ship block overlapping that world cell.
@Mixin(
    value = WaterBoundPathNavigation.class,
    targets = {
        "net.minecraft.world.entity.monster.Strider$StriderPathNavigation",
        "net.minecraft.world.entity.animal.Turtle$TurtlePathNavigation"
    }
)
public abstract class MixinStableDestinationNavigation {

    @WrapOperation(
        method = "isStableDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$shipAwareCell(
        final Level level, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        final BlockState vanilla = original.call(level, worldPos);
        if (!vanilla.isAir() || !VSGameConfig.SERVER.getAiOnShips()) return vanilla;
        final BlockState shipBlock = ShipBlockQueries.findShipBlock(level, worldPos, s -> !s.isAir());
        return shipBlock != null ? shipBlock : vanilla;
    }
}
