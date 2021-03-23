package org.valkyrienskies.mod.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftClientDuck;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow
    @Final
    private MinecraftClient client;

    @Redirect(
        method = "updateTargetedEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"
        )
    )
    public HitResult modifyCrosshairTarget(final Entity receiver, final double maxDistance, final float tickDelta,
        final boolean includeFluids) {

        final HitResult original = entityRaycastNoTransform(receiver, maxDistance, tickDelta, includeFluids);
        ((MinecraftClientDuck) this.client).vs$setOriginalCrosshairTarget(original);

        return receiver.raycast(maxDistance, tickDelta, includeFluids);
    }

    /**
     * {@link Entity#raycast(double, float, boolean)} except the hit pos is not transformed
     */
    @Unique
    private static HitResult entityRaycastNoTransform(
        final Entity entity, final double maxDistance, final float tickDelta, final boolean includeFluids) {
        final Vec3d vec3d = entity.getCameraPosVec(tickDelta);
        final Vec3d vec3d2 = entity.getRotationVec(tickDelta);
        final Vec3d vec3d3 = vec3d.add(vec3d2.x * maxDistance, vec3d2.y * maxDistance, vec3d2.z * maxDistance);
        return RaycastUtilsKt.raycastIncludeShips(
            (ClientWorld) entity.world,
            new RaycastContext(
                vec3d,
                vec3d3,
                RaycastContext.ShapeType.OUTLINE,
                includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE,
                entity
            ),
            false
        );
    }

}
