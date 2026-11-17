package org.valkyrienskies.mod.common.render.batched;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.valkyrienskies.mod.common.distant.DistantShipSnapshots;

/**
 * The blocks of a distant ship, for meshing. The client has no light data for these ships, so every block gets full
 * sky light plus its own light emission: seen from far away, a ship's outside is what shows. Tints use the biome
 * below the ship.
 */
final class DistantShipBlockView implements BlockAndTintGetter {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private ClientLevel level;
    private DistantShipSnapshots.Decoded snapshot;
    private Holder<Biome> biome;

    DistantShipBlockView set(final ClientLevel level, final DistantShipSnapshots.Decoded snapshot,
        final Holder<Biome> biome) {
        this.level = level;
        this.snapshot = snapshot;
        this.biome = biome;
        return this;
    }

    void clear() {
        this.level = null;
        this.snapshot = null;
        this.biome = null;
    }

    @Override
    public float getShade(final Direction direction, final boolean shade) {
        return ShipShadeRemovingGetter.shipShade(level, direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getBrightness(final LightLayer layer, final BlockPos pos) {
        final long section = SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        final DataLayer data = (layer == LightLayer.SKY ? snapshot.skyLight : snapshot.blockLight).get(section);
        if (data != null) {
            return data.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
        return layer == LightLayer.SKY && level.dimensionType().hasSkyLight() ? 15 : 0;
    }

    @Override
    public int getRawBrightness(final BlockPos pos, final int darkening) {
        return Math.max(getBrightness(LightLayer.BLOCK, pos), getBrightness(LightLayer.SKY, pos) - darkening);
    }

    @Override
    public int getBlockTint(final BlockPos pos, final ColorResolver resolver) {
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public BlockEntity getBlockEntity(final BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(final BlockPos pos) {
        final LevelChunkSection section =
            snapshot.blocks.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        return section == null ? AIR : section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Override
    public FluidState getFluidState(final BlockPos pos) {
        return getBlockState(pos).getFluidState();
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
