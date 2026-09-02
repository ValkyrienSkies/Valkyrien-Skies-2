package org.valkyrienskies.mod.mixin.feature.structure_lookup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.QueryableShipData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(StructureManager.class)
public abstract class MixinStructureManager {

    @Shadow
    @Final
    private LevelAccessor level;

    @Unique
    private final AABBd valkyrienskies$queryAabb = new AABBd();

    @Unique
    private final Vector3d valkyrienskies$queryPos = new Vector3d();

    @Unique
    @Nullable
    private List<BlockPos> valkyrienskies$shipQueryPositions(final BlockPos pos) {
        if (!(this.level instanceof final ServerLevel serverLevel)) {
            return null;
        }
        final QueryableShipData<LoadedServerShip> loadedShips =
            VSGameUtilsKt.getShipObjectWorld(serverLevel).getLoadedShips();
        if (loadedShips.isEmpty() || VSGameUtilsKt.isBlockInShipyard(serverLevel, pos)) {
            return null;
        }
        final double centerX = pos.getX() + 0.5;
        final double centerY = pos.getY() + 0.5;
        final double centerZ = pos.getZ() + 0.5;
        valkyrienskies$queryAabb.setMin(centerX, centerY, centerZ).setMax(centerX, centerY, centerZ);
        List<BlockPos> result = null;
        for (final LoadedServerShip ship :
            loadedShips.getIntersecting(valkyrienskies$queryAabb, VSGameUtilsKt.getDimensionId(serverLevel))) {
            final Vector3d shipPos = ship.getWorldToShip()
                .transformPosition(valkyrienskies$queryPos.set(centerX, centerY, centerZ));
            BlockPos blockPos = BlockPos.containing(shipPos.x, shipPos.y, shipPos.z);
            if (serverLevel.getChunkSource().getChunkNow(blockPos.getX() >> 4, blockPos.getZ() >> 4) == null) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>();
            }
            result.add(blockPos);
        }
        return result;
    }

    @WrapMethod(
        method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"
    )
    private StructureStart includeShipsInStructureWithPieceAt(
        final BlockPos pos, final Structure structure, final Operation<StructureStart> original
    ) {
        final StructureStart result = original.call(pos, structure);
        if (result.isValid()) {
            return result;
        }
        final List<BlockPos> shipPositions = valkyrienskies$shipQueryPositions(pos);
        if (shipPositions != null) {
            for (final BlockPos shipPos : shipPositions) {
                final StructureStart shipResult = original.call(shipPos, structure);
                if (shipResult.isValid()) {
                    return shipResult;
                }
            }
        }
        return result;
    }

    @WrapMethod(
        method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/tags/TagKey;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"
    )
    private StructureStart includeShipsInStructureWithPieceAtTag(
        final BlockPos pos, final TagKey<Structure> tag, final Operation<StructureStart> original
    ) {
        final StructureStart result = original.call(pos, tag);
        if (result.isValid()) {
            return result;
        }
        final List<BlockPos> shipPositions = valkyrienskies$shipQueryPositions(pos);
        if (shipPositions != null) {
            for (final BlockPos shipPos : shipPositions) {
                final StructureStart shipResult = original.call(shipPos, tag);
                if (shipResult.isValid()) {
                    return shipResult;
                }
            }
        }
        return result;
    }

    @WrapMethod(method = "getStructureAt")
    private StructureStart includeShipsInStructureAt(
        final BlockPos pos, final Structure structure, final Operation<StructureStart> original
    ) {
        final StructureStart result = original.call(pos, structure);
        if (result.isValid()) {
            return result;
        }
        final List<BlockPos> shipPositions = valkyrienskies$shipQueryPositions(pos);
        if (shipPositions != null) {
            for (final BlockPos shipPos : shipPositions) {
                final StructureStart shipResult = original.call(shipPos, structure);
                if (shipResult.isValid()) {
                    return shipResult;
                }
            }
        }
        return result;
    }

    @WrapMethod(method = "structureHasPieceAt")
    private boolean includeShipsInStructureHasPieceAt(
        final BlockPos pos, final StructureStart start, final Operation<Boolean> original
    ) {
        if (original.call(pos, start)) {
            return true;
        }
        final List<BlockPos> shipPositions = valkyrienskies$shipQueryPositions(pos);
        if (shipPositions != null) {
            for (final BlockPos shipPos : shipPositions) {
                if (original.call(shipPos, start)) {
                    return true;
                }
            }
        }
        return false;
    }

    @WrapMethod(method = "hasAnyStructureAt")
    private boolean includeShipsInHasAnyStructureAt(final BlockPos pos, final Operation<Boolean> original) {
        if (original.call(pos)) {
            return true;
        }
        final List<BlockPos> shipPositions = valkyrienskies$shipQueryPositions(pos);
        if (shipPositions != null) {
            for (final BlockPos shipPos : shipPositions) {
                if (original.call(shipPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    @WrapMethod(method = "getAllStructuresAt")
    private Map<Structure, LongSet> includeShipsInGetAllStructuresAt(
        final BlockPos pos, final Operation<Map<Structure, LongSet>> original
    ) {
        final Map<Structure, LongSet> result = original.call(pos);
        final List<BlockPos> shipPositions = valkyrienskies$shipQueryPositions(pos);
        if (shipPositions == null) {
            return result;
        }
        Map<Structure, LongSet> merged = null;
        for (final BlockPos shipPos : shipPositions) {
            final Map<Structure, LongSet> shipResult = original.call(shipPos);
            if (shipResult.isEmpty()) {
                continue;
            }
            if (merged == null) {
                merged = new HashMap<>(result);
            }
            for (final Map.Entry<Structure, LongSet> entry : shipResult.entrySet()) {
                final LongSet existing = merged.get(entry.getKey());
                if (existing == null) {
                    merged.put(entry.getKey(), entry.getValue());
                } else {
                    final LongOpenHashSet combined = new LongOpenHashSet(existing);
                    combined.addAll(entry.getValue());
                    merged.put(entry.getKey(), combined);
                }
            }
        }
        return merged == null ? result : merged;
    }
}
