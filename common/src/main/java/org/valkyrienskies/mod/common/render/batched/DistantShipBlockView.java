package org.valkyrienskies.mod.common.render.batched;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * The blocks of a distant ship, for meshing. The client has no light data for these ships, so every block gets full
 * sky light plus its own light emission: seen from far away, a ship's outside is what shows. Tints use the biome
 * below the ship.
 */
final class DistantShipBlockView implements BlockAndTintGetter {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private ClientLevel level;
    private Long2ObjectMap<LevelChunkSection> sections;
    private Holder<Biome> biome;

    DistantShipBlockView set(final ClientLevel level, final Long2ObjectMap<LevelChunkSection> sections,
        final Holder<Biome> biome) {
        this.level = level;
        this.sections = sections;
        this.biome = biome;
        return this;
    }

    void clear() {
        this.level = null;
        this.sections = null;
        this.biome = null;
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
    public int getBrightness(final LightLayer layer, final BlockPos pos) {
        return layer == LightLayer.SKY ? 15 : getBlockState(pos).getLightEmission();
    }

    @Override
    public int getRawBrightness(final BlockPos pos, final int darkening) {
        return Math.max(15 - darkening, getBlockState(pos).getLightEmission());
    }

    @Override
    public boolean canSeeSky(final BlockPos pos) {
        return true;
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
            sections.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
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
