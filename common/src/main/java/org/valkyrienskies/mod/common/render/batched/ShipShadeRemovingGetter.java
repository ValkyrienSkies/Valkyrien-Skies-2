package org.valkyrienskies.mod.common.render.batched;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

public final class ShipShadeRemovingGetter implements BlockAndTintGetter {

    private ClientLevel level;

    public ShipShadeRemovingGetter forLevel(final ClientLevel level) {
        this.level = level;
        return this;
    }

    @Override
    public float getShade(final Direction direction, final boolean shade) {
        return 1.0f;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getBlockTint(final BlockPos blockPos, final ColorResolver colorResolver) {
        return level.getBlockTint(blockPos, colorResolver);
    }

    @Override
    public BlockEntity getBlockEntity(final BlockPos blockPos) {
        return level.getBlockEntity(blockPos);
    }

    @Override
    public BlockState getBlockState(final BlockPos blockPos) {
        return level.getBlockState(blockPos);
    }

    @Override
    public FluidState getFluidState(final BlockPos blockPos) {
        return level.getFluidState(blockPos);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }
}
