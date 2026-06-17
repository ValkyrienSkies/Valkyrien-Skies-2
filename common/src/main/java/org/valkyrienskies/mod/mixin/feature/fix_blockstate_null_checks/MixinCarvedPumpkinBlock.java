package org.valkyrienskies.mod.mixin.feature.fix_blockstate_null_checks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Might become redundant if assembly is rewritten in some different way.
@Mixin(CarvedPumpkinBlock.class)
public class MixinCarvedPumpkinBlock {
    // This mixin is not a thing of honor... no highly esteemed deed is commemorated here... nothing valued is here.
    // As this block is relocated to a ship (assembly) it runs a check for spawning a golem.
    // The check involves checking blockstates of neighboring blocks which might be null, leading to a NPE.
    @WrapOperation(method = {
        "method_51167", "method_51168"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"))
    private static boolean nullCheck(BlockState instance, Operation<Boolean> original) {
        if (instance == null) return false;
        return original.call(instance);
    }
}
