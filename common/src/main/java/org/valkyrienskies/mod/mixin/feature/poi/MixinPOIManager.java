package org.valkyrienskies.mod.mixin.feature.poi;

import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.POIChunkSearcher;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

/**
 * @author Tomato
 * This atrocious mess of a mixin allows POIs in ship space to be detected, however it requires further mixins to Goals for the ship space positions to be correctly used, from what I can understand.
 */
// Priority 1100 so that the getInRange overwrite below also applies over Lithium/Canary/Radium's PoiManagerMixin (priority 1000),
// which overwrites getInRange with its own search. See issue #1788.
@Mixin(value = PoiManager.class, priority = 1100)
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
        final AABB aABB = new AABB(blockPos).inflate((double) i + 1);
        Stream<ChunkPos> chunkRange = ChunkPos.rangeClosed(new ChunkPos(blockPos), j);
        if (this.valkyrienskies$sLevel instanceof ServerLevel sLevel) {
            for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld(sLevel).getLoadedShips().getIntersecting(
                VectorConversionsMCKt.toJOML(aABB), VSGameUtilsKt.getDimensionId(sLevel))) {
                Vector4ic chunkRangeBounds = POIChunkSearcher.INSTANCE.shipChunkBounds(ship.getActiveChunksSet());
                if (chunkRangeBounds == null) {
                    continue;
                }
                chunkRange = Stream.concat(chunkRange, ChunkPos.rangeClosed(new ChunkPos(chunkRangeBounds.x(), chunkRangeBounds.y()),
                        new ChunkPos(chunkRangeBounds.z(), chunkRangeBounds.w())));
            }
        }
        return chunkRange.flatMap((chunkPos) -> this.getInChunk(predicate, chunkPos, occupancy)).filter((poiRecord) -> {
            BlockPos blockPos2 = poiRecord.getPos();
            Vec3 vecPos = VSGameUtilsKt.toWorldCoordinates(valkyrienskies$sLevel, new Vec3(blockPos2.getX(), blockPos2.getY(), blockPos2.getZ()));
            return Math.abs(vecPos.x() - blockPos.getX()) <= i && Math.abs(vecPos.z() - blockPos.getZ()) <= i;
        });
    }

    /**
     * @author Mrgregles
     * @reason The vanilla body with the distance measured in world space, so POIs aboard ships are found. An overwrite
     * rather than a wrap of the Stream.filter call: Lithium-style optimisers (Canary on Forge, Radium) overwrite
     * getInRange, after which the wrap has no target and Mixin refuses to apply this class, crashing on world load.
     */
    @Overwrite
    public Stream<PoiRecord> getInRange(Predicate<PoiType> predicate, BlockPos blockPos, int i, Occupancy occupancy) {
        final int k = i * i;
        return this.getInSquare(predicate, blockPos, i, occupancy).filter(poiRecord ->
            POIChunkSearcher.INSTANCE.getWorldPos(poiRecord, this.valkyrienskies$sLevel).distanceToSqr(Vec3.atLowerCornerOf(blockPos)) <= (double) k);
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
