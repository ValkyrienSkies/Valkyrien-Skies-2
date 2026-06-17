package org.valkyrienskies.mod.mixin.mod_compat.vista;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ViewFinderAccess.Block.class)
public abstract class ViewFinderAccessMixin {

    @WrapOperation(method = "stillValid", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"))
    private static double distToWorldPos(BlockPos blockPos, Position pos, Operation<Double> original){
        Level level = Minecraft.getInstance().level;
        if(VSGameUtilsKt.isBlockInShipyard(level, blockPos)) {
            return VSGameUtilsKt.toWorldCoordinates(level, blockPos.getCenter()).distanceToSqr((Vec3) pos);
        } else return original.call(blockPos, pos);
    }
}
