package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.content.trains.track.TrackBlockOutline.BezierPointSelection;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHighlightEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = TrackBlockOutline.class)
public class MixinTrackBlockOutline {
    @Unique
    private static Vec3 valkyrienskies$cameraVec3;
    @Unique
    private static Vec3 valkyrienskies$vec;
    @Unique
    private static Vec3 valkyrienskies$angles;

    @Inject(method = "drawCurveSelection", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/track/TrackBlockOutline$BezierPointSelection;angles()Lnet/minecraft/world/phys/Vec3;"), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private static void harvestDrawCurveSelection(final PoseStack ms, final MultiBufferSource buffer, final Vec3 camera,
        final CallbackInfo ci, final Minecraft mc,
        final BezierPointSelection result, final VertexConsumer vb, final Vec3 vec) {
        valkyrienskies$cameraVec3 = camera;
        valkyrienskies$vec = result.vec();
        valkyrienskies$angles = result.angles();
    }
    @ModifyArg(method = "drawCurveSelection",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/TrackBlockOutline;renderShape(Lnet/minecraft/world/phys/shapes/VoxelShape;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Ljava/lang/Boolean;)V"),
        index = 1,
        remap = false)
    private static PoseStack redirectTransformStackTranslate(final PoseStack ms) {

        final Level level = Minecraft.getInstance().level;
        if (level != null && valkyrienskies$vec != null) {
            final ClientShip ship;
            if ((ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, valkyrienskies$vec)) != null) {
                final Quaterniond rotation = new Quaterniond().identity();
                final Quaterniond yawQuat = new Quaterniond().rotateY(valkyrienskies$angles.y);
                final Quaterniond pitchQuat = new Quaterniond().rotateX(valkyrienskies$angles.x);

                yawQuat.mul(pitchQuat, rotation);
                ship.getRenderTransform().getShipToWorldRotation().mul(rotation, rotation);

                final Vector3d worldVec = ship.getRenderTransform().getShipToWorld()
                    .transformPosition(
                        new Vector3d(valkyrienskies$vec.x, valkyrienskies$vec.y + .125, valkyrienskies$vec.z),
                        new Vector3d());

                ms.popPose();
                ms.pushPose();
                ms.translate(worldVec.x - valkyrienskies$cameraVec3.x,
                    worldVec.y - valkyrienskies$cameraVec3.y,
                    worldVec.z - valkyrienskies$cameraVec3.z);
                ms.mulPose(VectorConversionsMCKt.toMinecraft(rotation));
                ms.translate(-.5, -.125f, -.5);
            }
        }
        return ms;
    }

    @Unique
    private static Camera valkyrienskies$info;
    @Unique
    private static BlockHitResult valkyrienskies$hitResult;

    @ModifyArg(method = "drawCustomBlockSelection", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"))
    private static BlockPos modIsWithinBounds(final BlockPos blockPos) {
        final Level level = Minecraft.getInstance().level;
        if (level != null) {
            final Ship ship;
            if ((ship = VSGameUtilsKt.getShipManagingPos(level, blockPos)) != null) {
                return new BlockPos(VectorConversionsMCKt.toMinecraft(ship.getShipToWorld()
                    .transformPosition(VectorConversionsMCKt.toJOMLD(blockPos))));
            }
        }
        return blockPos;
    }

    @Inject(method = "drawCustomBlockSelection", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void harvest(RenderHighlightEvent.Block event, CallbackInfo ci) {
        valkyrienskies$info = event.getCamera();
        valkyrienskies$hitResult = (BlockHitResult) event.getTarget();
    }

    @Redirect(method = "drawCustomBlockSelection",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void redirectTranslate(final PoseStack instance, final double d, final double e, final double f) {
        final Level level = Minecraft.getInstance().level;
        if (level != null) {
            final ClientShip ship;
            if ((ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level,
                valkyrienskies$hitResult.getBlockPos())) != null) {
                final Vec3 camPos = valkyrienskies$info.getPosition();
                VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), instance,
                    valkyrienskies$hitResult.getBlockPos(),
                    camPos.x, camPos.y, camPos.z);
            } else {
                instance.translate(d, e, f);
            }
        } else {
            instance.translate(d, e, f);
        }
    }
}
