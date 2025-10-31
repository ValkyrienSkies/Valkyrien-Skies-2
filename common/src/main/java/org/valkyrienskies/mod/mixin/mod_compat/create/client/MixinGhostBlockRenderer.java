package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.ghostblock.GhostBlockParams;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.mod_compat.create.accessors.GhostBlockParamsAccessor;

@Mixin(targets = {"net.createmod.catnip.ghostblock.GhostBlockRenderer$DefaultGhostBlockRenderer",
        "net.createmod.catnip.ghostblock.GhostBlockRenderer$TransparentGhostBlockRenderer"})
public class MixinGhostBlockRenderer {

    @WrapMethod(
        method = "render"
    )
    private void wrapRender(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, GhostBlockParams params,
        Operation<Void> original){
        final BlockPos pos = ((GhostBlockParamsAccessor)params).getPos();
        final ClientShip ship = VSClientGameUtils.getClientShip(pos.getX(), pos.getY(), pos.getZ());
        if(ship != null) {
            ms.pushPose();
            final Vec3 cameraInShip = VectorConversionsMCKt.toMinecraft(ship.getRenderTransform().getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(camera)));
            ms.mulPose(VectorConversionsMCKt.toFloat(ship.getRenderTransform().getShipToWorldRotation()));
            ms.last().pose().scale(ship.getRenderTransform().getShipToWorldScaling().get(new Vector3f()));
            original.call(ms, buffer, cameraInShip, params);
            ms.popPose();
        }
        else{
            original.call(ms, buffer, camera, params);
        }
    }
}
