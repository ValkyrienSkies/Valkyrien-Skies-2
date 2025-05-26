package org.valkyrienskies.mod.forge.mixin.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(FireBlock.class)
public abstract class FireMixin {

    @Unique
    private boolean isModifyingFireTick = false;

    @Shadow
    @Final
    public static IntegerProperty AGE;

    @Inject(method = "tick", at = @At("RETURN"))
    public void fireTickMixin(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random,
        final CallbackInfo ci) {
        if (isModifyingFireTick) {
            return;
        }

        isModifyingFireTick = true;

        final double origX = pos.getX();
        final double origY = pos.getY();
        final double origZ = pos.getZ();

        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 3, (x, y, z) -> {
            final BlockPos newPos = BlockPos.containing(x, y, z);

            if (level.isWaterAt(newPos)) {
                level.removeBlock(pos, false);
            }

            final int i = state.getValue(AGE);

            final boolean bl2 = level.isRainingAt(newPos);
            final int k = bl2 ? -50 : 0;
            this.checkBurnOut(level, pos.east(), 300 + k, random, i, Direction.WEST);
            this.checkBurnOut(level, pos.west(), 300 + k, random, i, Direction.EAST);
            this.checkBurnOut(level, pos.below(), 250 + k, random, i, Direction.UP);
            this.checkBurnOut(level, pos.above(), 250 + k, random, i, Direction.DOWN);
            this.checkBurnOut(level, pos.north(), 300 + k, random, i, Direction.SOUTH);
            this.checkBurnOut(level, pos.south(), 300 + k, random, i, Direction.NORTH);
            final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int l = -1; l <= 1; ++l) {
                for (int m = -1; m <= 1; ++m) {
                    for (int n = -1; n <= 4; ++n) {
                        if (l != 0 || n != 0 || m != 0) {
                            int o = 100;
                            if (n > 1) {
                                o += (n - 1) * 100;
                            }

                            mutableBlockPos.setWithOffset(newPos, l, n, m);
                            final int p = this.getIgniteOdds(level, mutableBlockPos);
                            if (p > 0) {
                                int q = (p + 40 + level.getDifficulty().getId() * 7) / (i + 30);
                                if (bl2) {
                                    q /= 2;
                                }

                                if (q > 0 && random.nextInt(o) <= q
                                    && (!level.isRaining() || !this.isNearRain(level, mutableBlockPos))) {
                                    final int r = Math.min(15, i + random.nextInt(5) / 4);
                                    level.setBlock(mutableBlockPos, this.getStateWithAge(level, mutableBlockPos, r), 3);
                                }
                            }
                        }
                    }
                }
            }
        });

        isModifyingFireTick = false;

    }

    @Inject(method = "onPlace", at = @At("HEAD"))
    public void onPlaceMixin(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState,
        final boolean isMoving,
        final CallbackInfo ci) {
        final double origX = pos.getX();
        final double origY = pos.getY();
        final double origZ = pos.getZ();

        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 1, (x, y, z) -> {

            final BlockPos newPos = BlockPos.containing(x, y, z);
            if (level.isWaterAt(newPos)) {
                level.removeBlock(pos, false);
            }
        });
    }

    @Shadow
    private void checkBurnOut(final Level arg, final BlockPos arg2, final int k, final RandomSource random, final int l,
        final Direction face) {
    }

    @Shadow
    protected abstract BlockState getStateWithAge(LevelAccessor levelAccessor, BlockPos blockPos, int i);

    @Shadow
    protected abstract boolean isNearRain(Level level, BlockPos blockPos);

    @Shadow
    protected abstract int getIgniteOdds(LevelReader levelReader, BlockPos blockPos);
}
