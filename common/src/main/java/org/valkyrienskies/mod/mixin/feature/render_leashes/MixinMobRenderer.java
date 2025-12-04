package org.valkyrienskies.mod.mixin.feature.render_leashes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(MobRenderer.class)
public class MixinMobRenderer {

    // For leashes rendering
    @WrapOperation(method = "renderLeash", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getRopeHoldPosition(F)Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 getRopeHoldPosition(final Entity instance, final float partialTicks,
        final Operation<Vec3> getRopeHoldPosition) {
        final Vec3 origVec = getRopeHoldPosition.call(instance, partialTicks);
        final Vector3d vec = VectorConversionsMCKt.toJOML(origVec);

        final LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(instance.level(), vec);
        if (ship != null) {
            ship.getShipToWorld().transformPosition(vec);
            return VectorConversionsMCKt.toMinecraft(vec);
        } else {
            return origVec;
        }
    }

    /**
     * When a mob is leashed to a fence on a ship, the leash will not be rendered if the mob is outside the frustum
     * even if the leash knot is inside it. This happens because the leash is rendered together with the leashed mob.
     * In vanilla this problem is mitigated via checking whether the knot is inside the frustum.
     * <p>
     * Usually such checks are done with methods like {@link EntityRenderDispatcher#shouldRender}. For some reason here
     * {@link Frustum#isVisible} is called directly, and we cannot mixin it for a ship-aware coordinate transform
     * as Frustum does not operate on {@link Level}.
     */
    @Redirect(
        method = "shouldRender(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean isTransformedAABBVisible(final Frustum frustum, final AABB aABB, @Local final Entity leashHolder) {
        return frustum.isVisible(VSGameUtilsKt.transformRenderAABBToWorld(((ClientLevel) leashHolder.level()), leashHolder.position(),
            leashHolder.getBoundingBoxForCulling()));
    }
}
