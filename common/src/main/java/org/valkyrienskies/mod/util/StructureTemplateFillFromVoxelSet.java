package org.valkyrienskies.mod.util;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;

public interface StructureTemplateFillFromVoxelSet {
    void vs$fillFromVoxelSet(@NotNull Level level, @NotNull Iterable<BlockPos> voxels,
        @NotNull List<ServerShip> shipsBeingCopied, @NotNull Map<Long, Vector3d> centerPositions,
        @NotNull BlockPos min, @NotNull BlockPos max
        );
}
