package org.valkyrienskies.mod.mixin.feature.world_weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;findLightningTargetAround(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos vs2$lightningTargetIncludingShips(
        ServerLevel level, BlockPos pos, Operation<BlockPos> original,
        @Local(argsOnly = true) LevelChunk chunk
    ) {
        if (VSGameUtilsKt.getShipManagingPos(level, chunk.getPos()) == null) {
            return original.call(level, pos);
        }
        return CompatUtil.INSTANCE.getWorldHeightmapPosIncludingShips(level, Heightmap.Types.MOTION_BLOCKING, pos);
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;moveTo(Lnet/minecraft/world/phys/Vec3;)V"))
    private void vs2$attachLightningBoltToShip(
        LightningBolt bolt, Vec3 pos, Operation<Void> original,
        @Local(argsOnly = true) LevelChunk chunk
    ) {
        original.call(bolt, pos);
        Ship ship = VSGameUtilsKt.getShipManagingPos(bolt.level(), chunk.getPos());
        if (ship != null) {
            ((IEntityDraggingInformationProvider) (Object) bolt).vs$dragImmediately(ship);
        }
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos occlude(
        ServerLevel level, Types types, BlockPos pos, Operation<BlockPos> original,
        @Local(argsOnly = true) LevelChunk chunk, @Share("ship") LocalRef<Ship> shipRef,
        @Share("occluded") LocalRef<BlockPos> occludedRef
    ) {
        BlockPos result = original.call(level, types, pos);
        BlockPos failure = BlockPos.ZERO.above(level.getMinBuildHeight() - 1);

        // Simple comparison with no ray casting. Ships will not occlude each other or world.
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, chunk.getPos());
        shipRef.set(ship);
        if (ship != null) {
            BlockPos worldPos = BlockPos.containing(CompatUtil.INSTANCE.toSameSpaceAs(level, result.getCenter(), (Ship)null, ship));
            BlockPos worldHeight = original.call(level, types, worldPos);
            if (worldHeight.getY() > worldPos.getY()) { // the ship block is occluded by a world block above it
                // Return an invalid BlockPos for which no snowfall and freezing will ever occur.
                occludedRef.set(failure);
                return failure;
            }
        }
        return result;
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> useBiomeAtWorldPos(
        ServerLevel level, BlockPos pos, Operation<Holder<Biome>> original,
        @Local(argsOnly = true) LevelChunk chunk, @Share("ship") LocalRef<Ship> shipRef,
        @Share("occluded") LocalRef<BlockPos> occludedRef
    ) {
        if (pos == occludedRef.get()) {
            // The occluded position is below the world, where the biome can neither freeze nor snow and precipitation
            // only reaches the empty handler of VOID_AIR. Transforming it through the ship would land far outside the
            // world and sample uncached climate noise, so read a biome from the ticking chunk instead.
            ChunkPos chunkPos = chunk.getPos();
            return original.call(level, new BlockPos(chunkPos.getMinBlockX() + 8, pos.getY(), chunkPos.getMinBlockZ() + 8));
        }
        return original.call(level, BlockPos.containing(CompatUtil.INSTANCE.toSameSpaceAs(level, pos.getCenter(), (Ship)null, shipRef.get())));
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Precipitation usePrecipitationAtWorldPos(Biome instance, BlockPos pos, Operation<Precipitation> original, @Share("ship") LocalRef<Ship> shipRef) {
        ServerLevel level = ServerLevel.class.cast(this);
        return original.call(instance, BlockPos.containing(CompatUtil.INSTANCE.toSameSpaceAs(level, pos.getCenter(), (Ship)null, shipRef.get())));
    }
}
