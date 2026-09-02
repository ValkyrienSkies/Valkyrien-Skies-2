package org.valkyrienskies.mod.mixin.feature.stable_ship_texture_seed;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ChunkClaim;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/**
 * Blocks that reduce texture tiling by randomly rotating their baked model (grass, sand, concrete,
 * and similar full-cube blocks with weighted "y" rotation variants) pick that rotation via
 * {@link BlockBehaviour.BlockStateBase#getSeed(BlockPos)}, which hashes the block's raw world
 * {@link BlockPos}. This is called both by vanilla's own chunk section compiler and by our batched
 * ship renderer (indirectly, since {@code BlockRenderDispatcher#renderBatched} recomputes the seed
 * from the {@link BlockPos} it's given rather than trusting a pre-seeded {@code RandomSource}), so
 * fixing it here covers every render path (vanilla, Sodium/Embeddium, and our batched renderer).
 * <p>
 * Blocks that belong to a ship physically live in a separate "shipyard" storage location whose
 * coordinates are essentially arbitrary - wherever the shipyard happened to allocate chunks for that
 * particular ship - and completely unrelated to where the block visually appears to the player.
 * Hashing off of that raw storage position makes the chosen texture variant effectively
 * re-randomized, which is commonly seen as textures on affected blocks appearing rotated (frequently
 * by 90 degrees, since there are 4 equally likely variants) the moment a structure is assembled into
 * a ship, and again on every subsequent reassembly.
 * <p>
 * This mixin substitutes a position relative to the ship's own chunk claim for blocks that belong to
 * a ship, so the resulting seed - and therefore the rendered texture variant - stays stable
 * regardless of where the shipyard physically stores the ship's chunks.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockStateBaseGetSeed {

    @ModifyVariable(method = "getSeed", at = @At("HEAD"), argsOnly = true)
    private BlockPos vs$useShipLocalPosForSeed(final BlockPos pos) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null) {
            return pos;
        }

        final Ship ship = ValkyrienSkies.getShipManagingBlock(level, pos);
        if (ship == null) {
            return pos;
        }

        final ChunkClaim claim = ship.getChunkClaim();
        final int localX = pos.getX() - (claim.getXStart() << 4);
        final int localZ = pos.getZ() - (claim.getZStart() << 4);
        return new BlockPos(localX, pos.getY(), localZ);
    }
}
