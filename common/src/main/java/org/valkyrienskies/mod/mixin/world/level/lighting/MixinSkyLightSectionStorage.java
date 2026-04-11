package org.valkyrienskies.mod.mixin.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes a shared-reference bug in {@link SkyLightSectionStorage#createDataLayer(long)}.
 *
 * Vanilla's {@code createDataLayer} returns the <em>same object reference</em> from
 * {@code queuedSections} when queued data exists. {@code initializeSection} stores this
 * in {@code updatingSectionData}, so both maps point to the same {@link DataLayer}.
 *
 * When light propagation later runs, {@code getDataLayerToWrite} skips its copy-on-write
 * (because the section was already in {@code changedSections} from {@code initializeSection}),
 * so propagation <strong>mutates the shared object in-place</strong>. This overwrites the
 * server-computed light values. {@code markNewInconsistencies} then sees matching references
 * and skips the replacement, leaving corrupted data in {@code visibleSectionData}.
 *
 * For vanilla chunks this is harmless because client propagation recomputes the same
 * values the server did. For ship chunks (in the VS2 shipyard at extreme coordinates),
 * propagation produces wrong values — causing random dark patches that only fix when
 * a block break triggers fresh propagation on a clean DataLayer.
 *
 * The fix: always return a <em>copy</em> from {@code queuedSections}, so
 * {@code updatingSectionData} and {@code queuedSections} never share the same object.
 */
@Mixin(SkyLightSectionStorage.class)
public abstract class MixinSkyLightSectionStorage extends LayerLightSectionStorage {

    // Dummy constructor to satisfy the compiler — never called.
    private MixinSkyLightSectionStorage() {
        super(null, null, null);
    }

    @Inject(method = "createDataLayer", at = @At("HEAD"), cancellable = true)
    private void copyQueuedDataLayer(long sectionPos, CallbackInfoReturnable<DataLayer> cir) {
        final DataLayer queued = (DataLayer) this.queuedSections.get(sectionPos);
        if (queued != null) {
            cir.setReturnValue(queued.copy());
        }
    }
}
