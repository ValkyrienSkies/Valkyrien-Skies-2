package org.valkyrienskies.mod.mixin.feature.ai;

import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.POIChunkSearcher;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

/**
 * @author Tomato
 * This atrocious mess of a mixin allows POIs in ship space to be detected, however it requires further mixins to Goals for the ship space positions to be correctly used, from what I can understand.
 */
@Mixin(PoiManager.class)
public abstract class MixinPOIManager implements OfLevel {

    @Unique
    private Level valkyrienskies$sLevel;

    @Shadow
    public abstract Stream<PoiRecord> getInChunk(Predicate<PoiType> predicate, ChunkPos chunkPos, Occupancy occupancy);

    /**
     * @author Tomato
     * @reason Allows for ships to be considered as a valid POI, also this method sucks anyway.
     */
    @Overwrite
    public Stream<PoiRecord> getInSquare(Predicate<PoiType> predicate, BlockPos blockPos, int i, Occupancy occupancy) {
        int j = Math.floorDiv(i, 16) + 1;
        final AABB aABB = new AABB(blockPos).inflate((double) j +1);
        Stream<ChunkPos> chunkRange = ChunkPos.rangeClosed(new ChunkPos(blockPos), j);
        Stream<PoiRecord> shipPOIs = Stream.empty();
        if (this.valkyrienskies$sLevel != null) {
            for (LoadedShip ship : VSGameUtilsKt.getShipObjectWorld(this.valkyrienskies$sLevel).getLoadedShips().getIntersecting(
                VectorConversionsMCKt.toJOML(aABB), VSGameUtilsKt.getDimensionId(this.valkyrienskies$sLevel))) {
                Vector4ic chunkRangeBounds = POIChunkSearcher.INSTANCE.shipChunkBounds(ship.getActiveChunksSet());
                ChunkPos.rangeClosed(new ChunkPos(chunkRangeBounds.x(), chunkRangeBounds.z()),
                        new ChunkPos(chunkRangeBounds.y(), chunkRangeBounds.w())).flatMap((chunkPos) -> this.getInChunk(predicate, chunkPos, occupancy)).filter((poiRecord) -> {
                    BlockPos blockPos2 = poiRecord.getPos();
                    Vec3 vecPos = new Vec3(blockPos2.getX(), blockPos2.getY(), blockPos2.getZ());
                    VSGameUtilsKt.toWorldCoordinates(this.valkyrienskies$sLevel, vecPos);
                    return Math.abs(vecPos.x() - blockPos.getX()) <= i && Math.abs(vecPos.z() - blockPos.getZ()) <= i;
                });;
            }
        }
        final Stream<PoiRecord> worldPOIs = chunkRange.flatMap((chunkPos) -> this.getInChunk(predicate, chunkPos, occupancy)).filter((poiRecord) -> {
                BlockPos blockPos2 = poiRecord.getPos();
                return Math.abs(blockPos2.getX() - blockPos.getX()) <= i && Math.abs(blockPos2.getZ() - blockPos.getZ()) <= i;
            });
        return Stream.concat(worldPOIs, shipPOIs);
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
