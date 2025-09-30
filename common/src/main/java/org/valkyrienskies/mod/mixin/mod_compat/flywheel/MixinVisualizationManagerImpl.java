package org.valkyrienskies.mod.mixin.mod_compat.flywheel;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.MixinBlockEntityStorageDuck;

@Pseudo
@Mixin(value = VisualizationManagerImpl.class, remap = false)
public abstract class MixinVisualizationManagerImpl {
    @Shadow
    @Final
    private VisualManagerImpl<BlockEntity, BlockEntityStorage> blockEntities;

    /*
        Updates the posture of every ship registered to the storage at the start of the rendering.
     */
    @Inject(
        method = "render",
        at = @At("HEAD")
    )
    private void renderShipTiles(final RenderContext context, final CallbackInfo ci) {
        ((MixinBlockEntityStorageDuck) blockEntities.getStorage()).vs$updateAllShips();
    }
}
