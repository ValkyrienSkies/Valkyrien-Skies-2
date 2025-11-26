package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.track.TrackTargetingClient;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(TrackTargetingClient.class)
public class MixinTrackTargetingClient {
    @Shadow
    static BlockPos lastHovered;

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/world/phys/Vec3;)Ldev/engine_room/flywheel/lib/transform/Translate;")
    )
    private static Translate redirectWithShip(PoseTransformStack instance, Vec3 vec3, Operation<Translate> original, @Local(argsOnly = true) Vec3 camera) {
        ClientShip ship = VSClientGameUtils.getClientShip(lastHovered.getX(), lastHovered.getY(), lastHovered.getZ());
        if(ship != null) {
            Vector3d inWorldJOML = ship.getRenderTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(Vec3.atLowerCornerOf(lastHovered)));
            return instance.translate(inWorldJOML.x, inWorldJOML.y, inWorldJOML.z)
                .translate(-camera.x, -camera.y, -camera.z)
                .rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
        }
        else return original.call(instance, vec3);
    }
}
