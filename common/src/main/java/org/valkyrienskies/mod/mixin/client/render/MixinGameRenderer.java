package org.valkyrienskies.mod.mixin.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipObjectClientWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
        )
    )
    public HitResult modifyCrosshairTarget(final Entity receiver, final double maxDistance, final float tickDelta,
        final boolean includeFluids) {

        final HitResult original = entityRaycastNoTransform(receiver, maxDistance, tickDelta, includeFluids);
        ((MinecraftDuck) this.minecraft).vs$setOriginalCrosshairTarget(original);

        return receiver.pick(maxDistance, tickDelta, includeFluids);
    }

    /**
     * {@link Entity#pick(double, float, boolean)} except the hit pos is not transformed
     */
    @Unique
    private static HitResult entityRaycastNoTransform(
        final Entity entity, final double maxDistance, final float tickDelta, final boolean includeFluids) {
        final Vec3 vec3d = entity.getEyePosition(tickDelta);
        final Vec3 vec3d2 = entity.getViewVector(tickDelta);
        final Vec3 vec3d3 = vec3d.add(vec3d2.x * maxDistance, vec3d2.y * maxDistance, vec3d2.z * maxDistance);
        return RaycastUtilsKt.clipIncludeShips(
            (ClientLevel) entity.level,
            new ClipContext(
                vec3d,
                vec3d3,
                ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                entity
            ),
            false
        );
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(final float tickDelta, final long startTime, final boolean tick, final CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null) {
            // Update ship render transforms
            final ShipObjectClientWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(clientWorld);

            for (final ShipObjectClient shipObjectClient : shipWorld.getShipObjects().values()) {
                shipObjectClient.updateRenderShipTransform(tickDelta);
            }
        }
    }

}
