package org.valkyrienskies.mod.mixin.mod_compat.immersive_portals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames;
import org.valkyrienskies.mod.common.util.ClippedShipBox;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.Plane;

@Mixin(value = PortalRendering.class, remap = false)
public abstract class MixinPortalRendering {
    @WrapOperation(method = "shouldRenderHitResult", at = @At(value = "INVOKE",
        target = "Lqouteall/q_misc_util/my_util/Plane;isPointOnPositiveSide"))
    private static boolean vs$visibleBlockPiece(Plane plane, Vec3 center, Operation<Boolean> original) {
        var client = Minecraft.getInstance();
        if (client.level != null && client.hitResult instanceof BlockHitResult hit) {
            var pos = hit.getBlockPos();
            var ship = VSGameUtilsKt.getLoadedShipManagingPos(client.level, pos);
            if (ship != null) {
                var pieces = VSGameUtilsKt.getAllShips(client.level).getSplitPieces(ship.getId());
                if (!pieces.isEmpty()) {
                    var cell = new AABBd(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                    for (var physical : pieces) {
                        var piece = ShipPieceRenderFrames.rendered(physical);
                        var box = ClippedShipBox.Companion.clip(cell, piece);
                        if (box != null && box.getVertices().stream().anyMatch(p ->
                            original.call(plane, new Vec3(p.x, p.y, p.z)))) return true;
                    }
                    return false;
                }
            }
        }
        return original.call(plane, center);
    }
}
