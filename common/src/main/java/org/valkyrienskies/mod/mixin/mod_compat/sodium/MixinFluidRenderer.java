package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

@Mixin(FluidRenderer.class)
public class MixinFluidRenderer {

    @ModifyVariable(
        method = "render",
        at = @At("STORE"),
        ordinal = 0
    )
    private Material redirectMaterial(
        Material material,
        @Local(argsOnly = true) FluidState fluidState,
        @Local(argsOnly = true) WorldSlice world,
        @Local(argsOnly = true, ordinal = 0) BlockPos blockPos
    ) {
        if (!VSGameUtilsKt.isBlockInShipyard(((WorldSliceAccessor) (Object) world).getWorld(), blockPos)) {
            if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
                return SodiumCompat.AIR_POCKET_MATERIAL;
            }
        }

        return material;
    }
}
