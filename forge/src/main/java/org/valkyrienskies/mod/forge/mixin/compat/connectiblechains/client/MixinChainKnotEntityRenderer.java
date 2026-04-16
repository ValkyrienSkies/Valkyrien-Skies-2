package org.valkyrienskies.mod.forge.mixin.compat.connectiblechains.client;

import com.lilypuree.connectiblechains.chain.ChainLink;
import com.lilypuree.connectiblechains.client.render.entity.ChainKnotEntityRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = ChainKnotEntityRenderer.class, remap = false)
public abstract class MixinChainKnotEntityRenderer {
    @Inject(
        method = "renderChainLink",
        at = @At(
            value = "NEW",
            target = "org/joml/Vector3f" // After all the offset calculations, just before the mod starts doing math on these positions
        ))
    private void adjustPositions(ChainLink link, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumerProvider, CallbackInfo ci,
        @Local(ordinal = 3) LocalRef<Vec3> srcPos, @Local(ordinal = 4) LocalRef<Vec3> destPos) {
        ClientShip srcShip = (ClientShip)VSGameUtilsKt.getShipManaging(link.primary);
        ClientShip destShip = (ClientShip)VSGameUtilsKt.getShipManaging(link.secondary);
        if (srcShip != null || destShip != null) {
            // Both positions are transformed to worldspace. This makes proper vertical dripping trivial and allows
            // using the same code for ship-to-ship, ship-to-player, ship-to-world connections.
            // This conflicts with rotation of rendered shipyard entities, but we take care of that later.
            Vec3 newSrcPos = CompatUtil.INSTANCE.toSameSpaceAs(link.primary.level(), srcPos.get(), (Ship)null);
            Vec3 newDestPos = CompatUtil.INSTANCE.toSameSpaceAs(link.secondary.level(), destPos.get(), (Ship)null);
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

    @WrapOperation(
        method = "shouldRender(Lcom/lilypuree/connectiblechains/entity/ChainKnotEntity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;shouldRender(DDD)Z"
        ),
        remap = true
    )
    private boolean adjustHolderAABB(Entity chainHolder, double x, double y, double z, Operation<Boolean> original) {
        ClientShip ship = (ClientShip)VSGameUtilsKt.getShipManaging(chainHolder);
        if (ship != null) {
            Vector3d cameraPosInShip = ship.getRenderTransform().getWorldToShip().transformPosition(x, y, z, new Vector3d());
            return original.call(chainHolder, cameraPosInShip.x, cameraPosInShip.y, cameraPosInShip.z);
        }
        return original.call(chainHolder, x, y, z);
    }

    /**
     * In the Forge port a chain connecting two blocks is cached so to skip recalculating it every tick, as blocks don't move.
     * This is not applicable for inter-ship connections where blocks do indeed move.
     * Whether the chain is connected to a block is determined by checking if the other entity is also a chain knot.
     * Hijacking INSTANCEOF just to add a second condition to an if statement is peak mixin, isn't it?
     */
    @ModifyConstant(
        method = "renderChainLink",
        constant = @Constant(classValue = HangingEntity.class)
    )
    // According to Minecraft Development plugin for Idea, this is wrong. The signature it suggests is actually incorrect.
    // I have no idea what the leading Object is for, but the expected signature has it listed;
    private Class<?> disableBakingForShips(Object object, Class<?> constant, @Local(argsOnly = true) ChainLink link) {
        ClientShip srcShip = (ClientShip)VSGameUtilsKt.getShipManaging(link.primary);
        ClientShip destShip = (ClientShip)VSGameUtilsKt.getShipManaging(link.secondary);
        if (srcShip != null || destShip != null) {
            return Class.class; // or really any class that our entity is not instanceof
        } else {
            return constant;
        }
    }
}
