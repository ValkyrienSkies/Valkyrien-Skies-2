package org.valkyrienskies.mod.mixin.feature.ai.zombie_villager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipBlockQueries;

// ZombieVillager.getConversionProgress's 9x9x9 cure-acceleration scan reads getBlockState world-only, so ship-mounted iron bars / beds don't accelerate the cure. Not a correctness bug — the cure still completes, just at the slow base rate instead of the typical accelerated chamber rate.
@Mixin(ZombieVillager.class)
public abstract class MixinZombieVillagerConversionProgress {

    @WrapOperation(
        method = "getConversionProgress",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateIncludingShips(
        final Level instance, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(instance, worldPos);
        if (vs$matchesCureBlock(worldState)) return worldState;
        final BlockState shipState = ShipBlockQueries.findShipBlock(instance, worldPos, s -> vs$matchesCureBlock(s));
        if (shipState != null) return shipState;
        return worldState;
    }

    @Unique
    private static boolean vs$matchesCureBlock(final BlockState state) {
        return state.is(Blocks.IRON_BARS) || state.getBlock() instanceof BedBlock;
    }
}
