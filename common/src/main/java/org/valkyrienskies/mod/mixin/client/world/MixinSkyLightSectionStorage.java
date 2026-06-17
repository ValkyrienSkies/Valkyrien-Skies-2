package org.valkyrienskies.mod.mixin.client.world;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

/**
 * Force shipyard-chunk sky {@code createDataLayer} to return a pre-filled
 * sky=15 layer regardless of {@code lightOnInSection} or any queued data.
 *
 * <p>Context: when vanilla's {@code enableChunkLight} triggers
 * {@code initializeSection} on a neighbor section via the 27-neighbor
 * {@code neighborCount} cascade in
 * {@link net.minecraft.world.level.lighting.LayerLightSectionStorage#updateSectionStatus},
 * the freshly-created DataLayer is determined by
 * {@link SkyLightSectionStorage#createDataLayer(long)}. Vanilla's branch:
 * <pre>
 *   return lightOnInSection(l) ? new DataLayer(15) : new DataLayer();
 * </pre>
 * If {@code setLightEnabled} on the empty neighbor's column hasn't been
 * called yet, {@code lightOnInSection} returns false and the layer is
 * zero-filled — which then becomes "sky is 0 at every position in that
 * section," visible to the mesh compile as dark faces facing into the
 * empty neighbor.
 *
 * <p>Timing-based fixes (pre-enabling the 3x3 neighborhood at the TAIL
 * of {@code applyLightData}) couldn't reliably win the race against the
 * cascade even when the {@code lightOnInSection} key was set before the
 * cascade ran. Rather than chase the ordering, this mixin intercepts
 * the exact choice point and forces shipyard sections into the 15-filled
 * path unconditionally. Subsequent light-engine propagation writes
 * whatever values the actual block shadowing demands; the 15-fill is
 * just the correct initial state for "no opaque blocks above."
 *
 * <p>Only fires for shipyard coords — vanilla world chunks go through
 * the normal branch.
 */
@Mixin(SkyLightSectionStorage.class)
public abstract class MixinSkyLightSectionStorage {

    @ModifyReturnValue(
        method = "createDataLayer(J)Lnet/minecraft/world/level/chunk/DataLayer;",
        at = @At("RETURN")
    )
    private DataLayer vs$shipyardSkyDefaultFifteen(final DataLayer original, final long sectionPos) {
        final int cx = SectionPos.x(sectionPos);
        final int cz = SectionPos.z(sectionPos);
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
            return original;
        }
        if (original == null || !original.isEmpty()) {
            return original;
        }

        // Narrow the fill-15 override to shipyard chunks that have NO ship
        // blocks (empty neighbor chunks around an actual ship). Chunks
        // with ship blocks — including hollow-ship interiors — must go
        // through vanilla's normal {@code lightOnInSection} branch so
        // enclosed sections start at 0 and don't leak sky.
        //
        // The fix is only needed for empty neighbors because they're the
        // ones whose {@code createDataLayer} can race ahead of our
        // {@code setLightEnabled} (the {@code enableChunkLight} cascade's
        // 27-neighbor loop can trigger initializeSection on an empty
        // neighbor's section before our TAIL-hook pre-enable runs for
        // the OTHER ship chunks that make up the same ship). For
        // chunks that actually have ship blocks, vanilla's
        // {@code lightOnInSection} is set correctly by the packet's own
        // {@code applyLightData} call before any cascade fires.
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            // Probably running on the server's SkyLightSectionStorage —
            // leave vanilla alone.
            return original;
        }
        if (mc.level.getChunkSource() instanceof ClientChunkCacheDuck duck
            && duck.vs$getShipChunks().containsKey(ChunkPos.asLong(cx, cz))) {
            // Chunk has ship blocks — let vanilla decide (matters for
            // hollow ships: enclosed interiors must start at 0).
            return original;
        }
        return new DataLayer(15);
    }
}
