package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Maps Block instances to the {@link VsShipBiomeColorStorage} resolver slot
 * whose color should be applied at fragment time. The Sodium chunk mesher
 * mixin reads this to set the {@code resolverType} bits in the per-vertex AO
 * byte and to skip baking the BlockColors multiplier.
 *
 * <p>Returns {@code 0} for blocks whose vertex color does not depend on biome
 * (the BlockColors bake stays as-is in the vertex). Returns {@code 1/2/3} for
 * grass / foliage / water respectively, matching {@link VsShipBiomeColorStorage}'s
 * RESOLVER_* constants offset by +1 so that 0 means "no tint".
 *
 * <p>Limited to vanilla blocks. Modded blocks that use biome resolvers will
 * keep their static-baked tint (i.e. the shipyard biome's color, possibly
 * remapped to world coords by MixinClientLevel) and won't update as the ship
 * moves.
 */
public final class BiomeColorResolvers {
    public static final int NONE = 0;
    public static final int GRASS = 1;
    public static final int FOLIAGE = 2;
    public static final int WATER = 3;

    private static final Reference2IntMap<Block> BLOCK_TO_RESOLVER = new Reference2IntOpenHashMap<>();

    static {
        BLOCK_TO_RESOLVER.defaultReturnValue(NONE);

        // Grass-color resolver (uses BiomeColors.GRASS_COLOR_RESOLVER in vanilla).
        BLOCK_TO_RESOLVER.put(Blocks.GRASS_BLOCK, GRASS);
        BLOCK_TO_RESOLVER.put(Blocks.GRASS, GRASS);
        BLOCK_TO_RESOLVER.put(Blocks.TALL_GRASS, GRASS);
        BLOCK_TO_RESOLVER.put(Blocks.FERN, GRASS);
        BLOCK_TO_RESOLVER.put(Blocks.LARGE_FERN, GRASS);
        BLOCK_TO_RESOLVER.put(Blocks.POTTED_FERN, GRASS);
        BLOCK_TO_RESOLVER.put(Blocks.SUGAR_CANE, GRASS);

        // Foliage-color resolver.
        BLOCK_TO_RESOLVER.put(Blocks.OAK_LEAVES, FOLIAGE);
        BLOCK_TO_RESOLVER.put(Blocks.JUNGLE_LEAVES, FOLIAGE);
        BLOCK_TO_RESOLVER.put(Blocks.ACACIA_LEAVES, FOLIAGE);
        BLOCK_TO_RESOLVER.put(Blocks.DARK_OAK_LEAVES, FOLIAGE);
        BLOCK_TO_RESOLVER.put(Blocks.MANGROVE_LEAVES, FOLIAGE);
        BLOCK_TO_RESOLVER.put(Blocks.VINE, FOLIAGE);

        // Water-color resolver.
        BLOCK_TO_RESOLVER.put(Blocks.WATER, WATER);
        BLOCK_TO_RESOLVER.put(Blocks.BUBBLE_COLUMN, WATER);
        BLOCK_TO_RESOLVER.put(Blocks.WATER_CAULDRON, WATER);
    }

    /** Returns the resolver index (0=none, 1=grass, 2=foliage, 3=water) for {@code block}. */
    public static int forBlock(Block block) {
        return BLOCK_TO_RESOLVER.getInt(block);
    }

    private BiomeColorResolvers() {}
}
