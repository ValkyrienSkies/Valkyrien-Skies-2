package org.valkyrienskies.mod.mixin.feature.ai.goal.cat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla reads at player.blockPosition() (world); ship beds live in shipyard, so that
// cell is air. Read at sleepingPos instead — vanilla wrote the bed pos there directly,
// avoiding the worldToShip round-trip whose FP drift on rotated ships intermittently
// floors to a neighbour cell.
@Mixin(targets = "net.minecraft.world.entity.animal.Cat$CatRelaxOnOwnerGoal")
public abstract class MixinCatRelaxOnOwnerGoal {

    @Shadow
    @Nullable
    private Player ownerPlayer;

    @WrapOperation(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$shipAwareBedLookup(final Level level, final BlockPos worldPos,
        final Operation<BlockState> original) {
        final BlockState vanilla = original.call(level, worldPos);
        if (vanilla.is(BlockTags.BEDS)) return vanilla;

        final Player player = this.ownerPlayer;
        if (player == null) return vanilla;
        final Optional<BlockPos> sleepingPos = player.getSleepingPos();
        if (sleepingPos.isEmpty()) return vanilla;
        final BlockState bedState = level.getBlockState(sleepingPos.get());
        return bedState.is(BlockTags.BEDS) ? bedState : vanilla;
    }
}
