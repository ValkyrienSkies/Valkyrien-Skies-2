package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.placement.IPlacementHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(IPlacementHelper.class)
public interface MixinPlacementHelpers {
    @Redirect(method = "orderedByDistance(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/Vec3;Ljava/util/Collection;)Ljava/util/List;", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectGetCenterOf(Vec3i pos) {
        Vec3 result = VecHelper.getCenterOf(pos);
        Level world = Minecraft.getInstance().level;
        if (world != null && VSGameUtilsKt.isBlockInShipyard(world, pos.getX(),pos.getY(),pos.getZ()) && VSGameUtilsKt.getShipManagingPos(world, pos.getX(),pos.getY(),pos.getZ()) instanceof ClientShip ship) {
            Vector3d tempVec = new Vector3d(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            ship.getShipToWorld().transformPosition(tempVec, tempVec);
            result = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return result;
    }
}
