package org.valkyrienskies.mod.mixin.mod_compat.common_create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(
    targets = {
        // Create v0.5.1
        "com/simibubi/create/foundation/utility/ghost/GhostBlockRenderer$DefaultGhostBlockRenderer",
        "com/simibubi/create/foundation/utility/ghost/GhostBlockRenderer$TransparentGhostBlockRenderer",
        // Create v6
        "net/createmod/catnip/ghostblock/GhostBlockRenderer$DefaultGhostBlockRenderer",
        "net/createmod/catnip/ghostblock/GhostBlockRenderer$TransparentGhostBlockRenderer",
    },
    remap = false
)
public class MixinGhostBlockRenderer {
    /**
     * @reason Floating point inaccuracy with shipyard translation.
     */
    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private void adjustMatrices(
        PoseStack instance, double d, double e, double f, Operation<Void> original,
        @Local(argsOnly = true) Vec3 camera, @Local BlockPos pos
    ) {
        final ClientLevel level = Minecraft.getInstance().level;
        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos);

        if (ship == null) {
            instance.translate(d, e, f);
        } else {
            // Remove the earlier applied translation
            instance.popPose();
            instance.pushPose();

            VSClientGameUtils.transformRenderWithShip(
                ship.getRenderTransform(), instance, pos, camera.x, camera.y, camera.z
            );
        }
    }
}
