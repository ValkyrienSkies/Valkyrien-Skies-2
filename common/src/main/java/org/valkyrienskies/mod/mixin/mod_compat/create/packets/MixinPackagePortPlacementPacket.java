package org.valkyrienskies.mod.mixin.mod_compat.create.packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.packagePort.PackagePortPlacementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(PackagePortPlacementPacket.class)
public class MixinPackagePortPlacementPacket {
    @Shadow
    private BlockPos pos;

    @WrapOperation(
        method = "lambda$handle$0",
        at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean adjustPositions(
        Vec3 targetLocation, Position thisPosition, double distance, Operation<Boolean> original,
        @Local Level level
    ) {
        return original.call(
            CompatUtil.INSTANCE.toSameSpaceAs(
                level, (Vec3)targetLocation, pos
            ), thisPosition, distance
        );
    }
}
