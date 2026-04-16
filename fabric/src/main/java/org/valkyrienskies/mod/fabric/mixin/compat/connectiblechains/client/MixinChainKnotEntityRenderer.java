package org.valkyrienskies.mod.fabric.mixin.compat.connectiblechains.client;

import com.github.legoatoom.connectiblechains.client.render.entity.ChainKnotEntityRenderer;
import com.github.legoatoom.connectiblechains.client.render.entity.state.ChainKnotEntityRenderState;
import com.github.legoatoom.connectiblechains.client.render.entity.state.ChainKnotEntityRenderState.ChainData;
import com.github.legoatoom.connectiblechains.entity.ChainKnotEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ChainKnotEntityRenderer.class)
public abstract class MixinChainKnotEntityRenderer {
    @Unique
    ClientLevel valkyrienskies$level;

    @Inject(
        method = "render(Lcom/github/legoatoom/connectiblechains/entity/ChainKnotEntity;Lcom/github/legoatoom/connectiblechains/client/render/entity/state/ChainKnotEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At("HEAD")
    )
    private void saveEntity(ChainKnotEntity entity, ChainKnotEntityRenderState state, PoseStack matrices,
        MultiBufferSource vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
        // One thing the Forge port does right, it exposes entities to `renderChainLink` via its ChainLink class.
        // While nice to have, knot/holder entities are not essential to our mixin but access to a Level is.
        // We store it as a mixin field on the assumption there's only one level the renderer is handling at the same
        // time. The assumption is fairly reasonable unless Immersive Portals reuses entity renderers for different levels.
        this.valkyrienskies$level = (ClientLevel)entity.level();
    }

    @WrapOperation(
        method = "shouldRender(Lcom/github/legoatoom/connectiblechains/entity/ChainKnotEntity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"
        ),
        require = 0 // Present in older versions of the mod, seems to be removed in a bugfix update
    )
    private boolean adjustHolderAABB(Frustum frustum, AABB aabb, Operation<Boolean> original, @Local Entity chainHolder) {
        return frustum.isVisible(VSGameUtilsKt.transformRenderAABBToWorld(((ClientLevel) chainHolder.level()), chainHolder.position(), aabb));
    }

    @Inject(
        method = "renderChainLink",
        at = @At(
            value = "NEW",
            target = "org/joml/Vector3f" // After all the offset calculations, just before the mod starts doing math on these positions
        ))
    private void adjustPositions(PoseStack matrices, MultiBufferSource vertexConsumerProvider, ChainData chainData, CallbackInfo ci,
        @Local(ordinal = 1) LocalRef<Vec3> srcPos, @Local(ordinal = 2) LocalRef<Vec3> destPos) {
        if (valkyrienskies$level == null) return;

        // We don't have access to entities acting as chain ends so instead of `getShipManaging` we have to rely on
        // determining ships from level and coordinates. It is possible to do better with a mixin to `render`, the
        // caller of this function.
        ClientShip srcShip = VSGameUtilsKt.getLoadedShipManagingPos(valkyrienskies$level, srcPos.get());
        ClientShip destShip = VSGameUtilsKt.getLoadedShipManagingPos(valkyrienskies$level, destPos.get());
        if (srcShip != null || destShip != null) {
            // Both positions are transformed to worldspace. This makes proper vertical dripping trivial and allows
            // using the same code for ship-to-ship, ship-to-player, ship-to-world connections.
            // This conflicts with rotation of rendered shipyard entities, but we take care of that later.
            Vec3 newSrcPos = CompatUtil.INSTANCE.toSameSpaceAs(valkyrienskies$level, srcPos.get(), (Ship)null);
            Vec3 newDestPos = CompatUtil.INSTANCE.toSameSpaceAs(valkyrienskies$level, destPos.get(), (Ship)null);
            srcPos.set(newSrcPos);
            if (srcShip != null) {
                Vector3dc scale = srcShip.getRenderTransform().getScaling();
                // destPos is in worldspace but the chain link is drawn an entity on a ship which is scaled.
                // To compensate for that, we artificially move destPos inversely to the ship scale.
                // After scaling is reapplied when the entity is rendered, the position will match the intended one.
                destPos.set(
                    newSrcPos.add(newDestPos.subtract(newSrcPos).multiply(1 / scale.x(), 1 / scale.y(), 1 / scale.z()))
                );
                // Negate the rotation that will happen when the shipyard entity is rendered. Scaling is preserved.
                matrices.mulPose(new Quaternionf(srcShip.getRenderTransform().getShipToWorldRotation()).invert());
            } else {
                destPos.set(newDestPos);
            }
        }
    }
}
