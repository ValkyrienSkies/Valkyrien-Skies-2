package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(PackagePortTargetSelectionHandler.class)
public class MixinPackagePortTargetSelectionHandler {
    @WrapMethod(method = "animateConnection")
    private static void animateConnectionInWorld(
        Minecraft mc, Vec3 source, Vec3 target, Color color, Operation<Void> original) {
        original.call(
            mc,
            VSGameUtilsKt.toWorldCoordinates(mc.level, source),
            VSGameUtilsKt.toWorldCoordinates(mc.level, target),
            color
        );
    }

    @WrapMethod(method = "validateDiff")
    private static String adjustPositions(
        Vec3 target, BlockPos placedPos, Operation<String> original
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        return original.call(
            CompatUtil.INSTANCE.toSameSpaceAs(
                level, target, placedPos
            ),
            placedPos
        );
    }
}
