package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.lib.math.MoreMath;
import dev.engine_room.flywheel.lib.visual.EntityVisibilityTester;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * The Entity Culling test for the AbstractEntityVisuals have problem with 'transposed' visuals.
 * This class shouldn't be needed but as of now(flywheel 1.0.4), it is necessary.
 * @author Bunting_chj
 */
@Mixin(EntityVisibilityTester.class)
public class MixinEntityVisibilityTester {
    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private float scale;

    @Inject(
        method = "adjustAndTestAABB",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cullingOnShip(FrustumIntersection frustum, AABB aabb, CallbackInfoReturnable<Boolean> cir){
        if(VSGameUtilsKt.getShipManaging(entity) instanceof ClientShip ship){
            final Vector3f pos = ship.getRenderTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(entity.position())).get(
                new Vector3f());
            final float maxSize = (float) Math.max(aabb.getXsize(), Math.max(aabb.getYsize(), aabb.getZsize()));
            if (frustum.testSphere(pos.x, pos.y, pos.z, maxSize * MoreMath.SQRT_3_OVER_2 * scale)){
                cir.setReturnValue(true);
            }
        }
    }
}
