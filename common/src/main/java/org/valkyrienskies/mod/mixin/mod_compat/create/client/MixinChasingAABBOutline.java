package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import static org.valkyrienskies.mod.common.VSGameUtilsKt.toWorldRenderCoordinates;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.ChasingAABBOutline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChasingAABBOutline.class)
public class MixinChasingAABBOutline extends AABBOutline {

    @Shadow
    AABB prevBB;
    @Shadow
    AABB targetBB;

    @Shadow
    private static AABB interpolateBBs(AABB current, AABB target, float pt) {
        throw new IllegalStateException("Mixin failed to apply");
    }

    public MixinChasingAABBOutline(AABB bb) {
        super(bb);
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/outliner/ChasingAABBOutline;renderBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lorg/joml/Vector4f;IZ)V"
        )
    )
    private void interpolatedPose(
        PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt, CallbackInfo ci
    ) {
        ms.pushPose();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ClientShip csTarget = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, targetBB.getCenter());
        if (csTarget == null) {
            return;
        }
        Quaterniondc rotTarget = csTarget.getRenderTransform().getShipToWorldRotation();
        Vec3 interBB = interpolateBBs(prevBB, bb, pt).getCenter();
        ms.translate(interBB.x() - camera.x, interBB.y() - camera.y, interBB.z() - camera.z);
        ms.mulPose(VectorConversionsMCKt.toFloat(rotTarget));
        ms.translate(camera.x - interBB.x(), camera.y - interBB.y(), camera.z - interBB.z());
    }

    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void postRender(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt, CallbackInfo ci) {
        ms.popPose();
    }

    @WrapMethod(method = "interpolateBBs")
    private static AABB interpolateBBsWrapped(
        AABB current,
        AABB target,
        float pt,
        Operation<AABB> original
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return original.call(current, target, pt);
        }

        return original.call(vs$transformAABBWithoutExpanding(level, current), vs$transformAABBWithoutExpanding(level, target), pt);
    }

    @Unique
    private static AABB vs$transformAABBWithoutExpanding(ClientLevel level, AABB aabb) {
        Vec3 currentCenter = aabb.getCenter();
        double maxX = aabb.maxX - currentCenter.x;
        double maxY = aabb.maxY - currentCenter.y;
        double maxZ = aabb.maxZ - currentCenter.z;
        double minX = aabb.minX - currentCenter.x;
        double minY = aabb.minY - currentCenter.y;
        double minZ = aabb.minZ - currentCenter.z;

        Vec3 newCenter = toWorldRenderCoordinates(level, currentCenter);
        return new AABB(
            minX + newCenter.x, minY + newCenter.y, minZ + newCenter.z,
            maxX + newCenter.x, maxY + newCenter.y, maxZ + newCenter.z
        );
    }
}
