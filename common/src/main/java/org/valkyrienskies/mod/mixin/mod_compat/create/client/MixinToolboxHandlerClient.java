package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.toolbox.ToolboxHandlerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(ToolboxHandlerClient.class)
public class MixinToolboxHandlerClient {
    @WrapOperation(
        method = {"onKeyInput", "renderOverlay"},
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/equipment/toolbox/ToolboxHandler;distance(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)D")
    )
    private static double adjustToolboxPos(Vec3 playerPos, BlockPos toolboxPos, Operation<Double> original) {
        // We cannot transform toolbox position to world because the original method accepts a Vec3 and a BlockPos.
        return original.call(
            CompatUtil.INSTANCE.toSameSpaceAs(Minecraft.getInstance().player.level(), playerPos, toolboxPos),
            toolboxPos
        );
    }
}
