package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.content.trains.track.TrackBlockOutline.BezierPointSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(TrackBlockOutline.class)
public class MixinTrackBlockOutline {
    @Shadow
    public static BezierPointSelection result;

    @WrapOperation(
        method = "pickCurves",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/track/BezierConnection;getBounds()Lnet/minecraft/world/phys/AABB;")
    )
    private static AABB getBoundsToWorld(BezierConnection connection, Operation<AABB> original) {
        return VSGameUtilsKt.transformAabbToWorld(Minecraft.getInstance().level, original.call(connection));
    }

    @WrapOperation(
        method = "pickCurves",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/track/BezierConnection;getPosition(D)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 getPositionToWorld(final BezierConnection connection, final double t, final Operation<Vec3> original){
        return VSGameUtilsKt.toWorldCoordinates(Minecraft.getInstance().level, original.call(connection, t));
    }

    @WrapOperation(
        // Two mixin targets. One for 6.0.7+, one for 6.0.6.
        // If we just use "drawCustomBlockSelection" it's not smart enough to match both
        method = {"Lcom/simibubi/create/content/trains/track/TrackBlockOutline;drawCustomBlockSelection(Lnet/minecraft/client/renderer/LevelRenderer;Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/HitResult;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)Z", "Lcom/simibubi/create/content/trains/track/TrackBlockOutline;drawCustomBlockSelection(Lnet/minecraftforge/client/event/RenderHighlightEvent$Block;)V"},
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private static void wrapSelection(PoseStack instance, double d, double e, double f, Operation<Void> original,
        @Local BlockPos hitPos, @Local Vec3 camPos){
        ClientShip ship = VSClientGameUtils.getClientShip(hitPos.getX(), hitPos.getY(), hitPos.getZ());
        if(ship != null) {
            Vector3d posInWorld = ship.getRenderTransform().getShipToWorld().transformPosition(hitPos.getX(), hitPos.getY(), hitPos.getZ(), new Vector3d());
            instance.translate(posInWorld.x - camPos.x, posInWorld.y - camPos.y, posInWorld.z - camPos.z);
            instance.mulPose(ship.getRenderTransform().getShipToWorld().getNormalizedRotation(new Quaternionf()));
        } else original.call(instance, d, e, f);
    }
}
