package org.valkyrienskies.mod.mixin.feature.poi;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector4ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.POIChunkSearcher;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;
import org.valkyrienskies.mod.mixinducks.world.OfShip;

/**
 * @author Tomato
 * This atrocious mess of a mixin allows POIs in ship space to be detected.
 */
@Mixin(PoiManager.class)
public abstract class MixinPOIManager implements OfLevel {

    @Unique
    private Level valkyrienskies$sLevel;


    @WrapMethod(method = "getInSquare")
    private Stream<PoiRecord> transformArgumentPose(Predicate<Holder<PoiType>> predicate, BlockPos blockPos, int i, Occupancy occupancy,
        Operation<Stream<PoiRecord>> original, @Share("originalPos") LocalRef<BlockPos> originalPos) {
        originalPos.set(blockPos);
        return original.call(predicate, this.valkyrienskies$sLevel == null ?
                blockPos :
                ValkyrienSkies.positionToWorld(this.valkyrienskies$sLevel, blockPos),
            i, occupancy);
    }

    @WrapOperation(method = "getInSquare", at = @At(value = "NEW", target = "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos useOriginalPos(BlockPos blockPos, Operation<ChunkPos> original, @Share("originalPos") LocalRef<BlockPos> originalPos) {
        return original.call(originalPos.get());
    }

    @WrapOperation(method = "getInSquare", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;rangeClosed(Lnet/minecraft/world/level/ChunkPos;I)Ljava/util/stream/Stream;"))
    private Stream<ChunkPos> addShipChunkToStream(ChunkPos chunkPos, int i, Operation<Stream<ChunkPos>> original, @Local(argsOnly = true) BlockPos seedWorld) {
        Stream<ChunkPos> chunkRange = original.call(chunkPos, i);
        final AABB aABB = new AABB(seedWorld, seedWorld).inflate((double) i + 1);
        if (this.valkyrienskies$sLevel instanceof ServerLevel sLevel) {
            for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld(sLevel).getLoadedShips().getIntersecting(
                ValkyrienSkies.toJOML(aABB), VSGameUtilsKt.getDimensionId(sLevel))) {
                Vector4ic chunkRangeBounds = POIChunkSearcher.INSTANCE.shipChunkBounds(ship.getActiveChunksSet());
                if (chunkRangeBounds == null) {
                    continue;
                }

                chunkRange = Stream.concat(chunkRange,
                    ChunkPos.rangeClosed(new ChunkPos(chunkRangeBounds.x(), chunkRangeBounds.y()),
                        new ChunkPos(chunkRangeBounds.z(), chunkRangeBounds.w())).peek((c) -> ((OfShip)c).setShip(ship)));
            }
        }
        // distinct(): a shipyard seed has its initial range and the ship-intersect loop
        // covering the same chunks, which would return each PoiRecord twice.
        return chunkRange.distinct();
    }

    //method_30335: getInSquare filter's lambda
    @WrapOperation(method = "method_30335", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiRecord;getPos()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos returnTransformedPos(PoiRecord instance, Operation<BlockPos> original) {
        BlockPos oPos = original.call(instance);
        if (oPos instanceof OfShip shipGetter && shipGetter.getShip() != null) {
            Ship ship = shipGetter.getShip();
            shipGetter.setShip((Ship) null);
            return ValkyrienSkies.positionToWorld(ship, oPos);
        }
        return oPos;
    }

    @WrapMethod(method = "getInChunk")
    private Stream<PoiRecord> getShipOnTheStream(Predicate<Holder<PoiType>> predicate, ChunkPos chunkPos, Occupancy occupancy,
        Operation<Stream<PoiRecord>> original) {
        return original.call(predicate, chunkPos, occupancy).peek((poi) -> {
            if (poi.getPos() instanceof OfShip shipHolder)
                shipHolder.setShip((OfShip) chunkPos);
        });
    }

    @WrapOperation(method = "getInRange", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private Stream<PoiRecord> onGetInRange(Stream<PoiRecord> instance, Predicate<? super PoiRecord> predicate, Operation<Stream<PoiRecord>> original, @Local(argsOnly = true) LocalRef<BlockPos> blockPos) {
        blockPos.set(ValkyrienSkies.positionToWorld(this.valkyrienskies$sLevel, blockPos.get()));
        return original.call(instance, predicate);
    }

    @Override
    public Level getLevel() {
        return valkyrienskies$sLevel;
    }

    @Override
    public void setLevel(Level level) {
        valkyrienskies$sLevel = level;
    }
}
