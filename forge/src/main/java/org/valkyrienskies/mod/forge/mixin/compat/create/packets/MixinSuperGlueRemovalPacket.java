package org.valkyrienskies.mod.forge.mixin.compat.create.packets;

import com.simibubi.create.content.contraptions.glue.SuperGlueRemovalPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SuperGlueRemovalPacket.class)
public abstract class MixinSuperGlueRemovalPacket {
    @Redirect(method = "handle", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double redirectPlayerDistanceToSqr(final ServerPlayer instance, final Vec3 vec3) {
        Vec3 newVec3 = vec3;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(instance.level(), vec3);
        if (ship != null) {
            newVec3 = VectorConversionsMCKt.toMinecraft(
                ship.getShipToWorld().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z)));
        }
        return instance.distanceToSqr(newVec3);
    }
}
