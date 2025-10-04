package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = AbstractEntityVisual.class, remap = false)
public abstract class MixinAbstractEntityVisual<T extends Entity> extends AbstractVisual implements EntityVisual<T> {

    @Shadow
    @Final
    protected T entity;

    private MixinAbstractEntityVisual(VisualizationContext ctx,
        Level level, float partialTick) {
        super(ctx, level, partialTick);
    }

    @Inject(
        method = "isVisible",
        at = @At("HEAD"),
        cancellable = true)
    private void disableCullingOnShip(final FrustumIntersection frustum, final CallbackInfoReturnable<Boolean> cir){
        if(VSGameUtilsKt.getShipManaging(entity) != null){
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
