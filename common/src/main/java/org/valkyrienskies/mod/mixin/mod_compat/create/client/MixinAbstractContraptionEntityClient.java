package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.MixinAbstractContraptionEntityDuck;

/**
 * See {@link org.valkyrienskies.mod.mixin.mod_compat.create.entity.MixinAbstractContraptionEntity} for where
 * most of this code came from. We just need it here as well so this mixin can be set to client only
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class MixinAbstractContraptionEntityClient extends Entity implements MixinAbstractContraptionEntityDuck {
    @Shadow(remap = false)
    protected Contraption contraption;

    public MixinAbstractContraptionEntityClient(EntityType<?> entityType,
        Level level) {
        super(entityType, level);
    }

    /**
     * If the contraption is moved between ship and world or other ship, the anchor should be moved too.
     * As far as I know this will only happen to carriage contraptions which can be relocated on tracks, but I'll add it here just in case other contraption entities are moved to a ship.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void updateAnchor(CallbackInfo ci) {
        if (VSGameUtilsKt.getShipManagingPos(level(), contraption.anchor) != VSGameUtilsKt.getShipManaging(this)) {
            this.contraption.anchor = this.blockPosition();
            if(VisualizationManager.supportsVisualization(level())) {
                VisualizationManager.get(level()).entities().queueRemove(this);
                VisualizationManager.get(level()).entities().queueAdd(this);
            }
        }
    }
}
