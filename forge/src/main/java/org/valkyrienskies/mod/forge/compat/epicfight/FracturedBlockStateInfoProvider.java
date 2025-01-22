package org.valkyrienskies.mod.forge.compat.epicfight;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.apigame.world.chunks.BlockType;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.BlockStateInfoProvider;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.world.level.block.FractureBlockState;

public class FracturedBlockStateInfoProvider implements BlockStateInfoProvider {
    public static void register() {
        Registry.register(BlockStateInfo.INSTANCE.getREGISTRY(),
            new ResourceLocation(EpicFightMod.MODID, "fractured"), new FracturedBlockStateInfoProvider());
    }

    @Override
    public int getPriority() {
        return 101;
    }

    @Nullable
    @Override
    public Double getBlockStateMass(@NotNull BlockState blockState) {
        if (blockState instanceof FractureBlockState)
            return 0.0;
        return null;
    }

    @Nullable
    @Override
    public BlockType getBlockStateType(@NotNull BlockState blockState) {
        if (blockState instanceof FractureBlockState)
            return ValkyrienSkiesMod.vsCore.getBlockTypes().getSolid();
        return null;
    }
}
