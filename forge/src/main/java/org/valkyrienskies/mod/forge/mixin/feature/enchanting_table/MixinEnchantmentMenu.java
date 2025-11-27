package org.valkyrienskies.mod.forge.mixin.feature.enchanting_table;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

// This mixin is only necessary because Forge decides to replace simpler vanilla code with its own, which includes an extra check.
@Mixin(EnchantmentMenu.class)
public class MixinEnchantmentMenu {
    @WrapOperation(method = "method_17411", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;", ordinal = -1))
    private static BlockPos correctOffset(BlockPos blockPos, Vec3i offset, Operation<BlockPos> original, @Local(argsOnly = true) Level level) {
        // Repeating some work already done. Still easier than replacing the entire lambda
        if (level.getBlockState(blockPos.offset(offset)).is(BlockTags.ENCHANTMENT_POWER_PROVIDER) && level.getBlockState(blockPos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2)).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) {
            return blockPos.offset(offset);
        } else {
            Vec3 worldCoordinates = VectorConversionsMCKt.toMinecraft(
                VSGameUtilsKt.getWorldCoordinates(level, blockPos, VectorConversionsMCKt.toJOML(blockPos.getCenter())));
            return BlockPos.containing(worldCoordinates).offset(offset);
        }
    }
}
