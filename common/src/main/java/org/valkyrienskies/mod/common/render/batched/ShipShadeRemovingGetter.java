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
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.config.VSGameConfig;

public final class ShipShadeRemovingGetter implements BlockAndTintGetter {

    private ClientLevel level;
    private ClientShip ship;
    private ShipBiomeTintCache biomeTints;

    public ShipShadeRemovingGetter forLevel(final ClientLevel level) {
        this.level = level;
        this.ship = null;
        this.biomeTints = null;
        return this;
    }

    ShipShadeRemovingGetter forShip(final ClientLevel level, final ClientShip ship, final ShipBiomeTintCache tints) {
        forLevel(level);
        this.ship = ship;
        this.biomeTints = tints;
        return this;
    }

    @Override
    public float getShade(final Direction direction, final boolean shade) {
        if (VSGameConfig.CLIENT.getBetterVanillaShipShading()) return 1.0f;
        final float strength = (float) Math.max(0.0, Math.min(1.0, VSGameConfig.CLIENT.getShipShadeStrength()));
        return 1.0f + (level.getShade(direction, shade) - 1.0f) * strength;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getBlockTint(final BlockPos blockPos, final ColorResolver colorResolver) {
        if (biomeTints != null && VSGameConfig.CLIENT.getDynamicShipBiomeTinting()) {
            return biomeTints.sample(level, ship, blockPos, colorResolver);
        }
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
