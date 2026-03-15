package org.valkyrienskies.mod.mixin.feature.world_weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @WrapOperation(
        method = "tickRain(Lnet/minecraft/client/Camera;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos includeShipsInWeatherTickOcclusion(
        final LevelReader levelReader, final Types types, final BlockPos pos, final Operation<BlockPos> original,
        @Share("weatherSurfaceLookupPos") final LocalRef<BlockPos> weatherSurfaceLookupPos
    ) {
        weatherSurfaceLookupPos.set(null);
        if (levelReader instanceof Level level) {
            final BlockPos vanillaHeight = original.call(levelReader, types, pos);
            final BlockPos worldHeight = BlockPos.containing(
                CompatUtil.INSTANCE.toSameSpaceAs(level, vanillaHeight.getCenter(), (Ship) null, null)
            );
            final BlockHitResult shipSurfaceHit =
                CompatUtil.INSTANCE.getShipHeightmapHitAboveWorldHeight(level, types, worldHeight);
            weatherSurfaceLookupPos.set(shipSurfaceHit == null ? null : shipSurfaceHit.getBlockPos().immutable());
            return shipSurfaceHit == null
                ? worldHeight
                : new BlockPos(worldHeight.getX(), Mth.floor(Math.nextDown(shipSurfaceHit.getLocation().y)) + 1, worldHeight.getZ());
        }
        return original.call(levelReader, types, pos);
    }

    @WrapOperation(
        method = "tickRain(Lnet/minecraft/client/Camera;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState includeShipsInWeatherTickSurfaceState(
        final LevelReader levelReader,
        final BlockPos pos,
        final Operation<BlockState> original,
        @Share("weatherSurfaceLookupPos") final LocalRef<BlockPos> weatherSurfaceLookupPos
    ) {
        final BlockPos lookupPos = weatherSurfaceLookupPos.get();
        return original.call(levelReader, lookupPos != null ? lookupPos : pos);
    }

    @WrapOperation(
        method = "tickRain(Lnet/minecraft/client/Camera;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState includeShipsInWeatherTickSurfaceFluid(
        final LevelReader levelReader,
        final BlockPos pos,
        final Operation<FluidState> original,
        @Share("weatherSurfaceLookupPos") final LocalRef<BlockPos> weatherSurfaceLookupPos
    ) {
        final BlockPos lookupPos = weatherSurfaceLookupPos.get();
        return original.call(levelReader, lookupPos != null ? lookupPos : pos);
    }

    @WrapOperation(
        method = "tickRain(Lnet/minecraft/client/Camera;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
        )
    )
    private VoxelShape includeShipsInWeatherTickCollisionShape(
        final BlockState state,
        final BlockGetter levelReader,
        final BlockPos pos,
        final Operation<VoxelShape> original,
        @Share("weatherSurfaceLookupPos") final LocalRef<BlockPos> weatherSurfaceLookupPos
    ) {
        final BlockPos lookupPos = weatherSurfaceLookupPos.get();
        // This fixes ship surface lookup, but vanilla still interprets the returned shape in world-axis block space. Solutions welcome?
        return original.call(state, levelReader, lookupPos != null ? lookupPos : pos);
    }

    @WrapOperation(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"
        )
    )
    private int includeShipsInWeatherRenderOcclusion(
        final Level level, final Types types, final int x, final int z, final Operation<Integer> original
    ) {
        return CompatUtil.INSTANCE.getWorldHeightIncludingShips(level, types, x, z);
    }
}
