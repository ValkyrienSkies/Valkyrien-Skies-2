package org.valkyrienskies.mod.mixin.feature.structure_template;

import com.google.common.collect.Lists;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.assembly.ICopyableBlock;

@Mixin(value = StructureTemplate.class)
public abstract class StructureTemplateMixin {

    @Shadow
    private Vec3i size;

    @Final
    @Shadow
    private List<StructureTemplate.Palette> palettes;

    @Final
    @Shadow
    private List<StructureTemplate.StructureEntityInfo> entityInfoList;

    @Shadow
    private static List<StructureTemplate.StructureBlockInfo> buildInfoList(List<StructureTemplate.StructureBlockInfo> basicBlocks, List<StructureTemplate.StructureBlockInfo> blocksWithEntities, List<StructureTemplate.StructureBlockInfo> specialBlocks) {
        return null;
    }

    @Shadow
    private static void addToLists(StructureTemplate.StructureBlockInfo blockInfo, List<StructureTemplate.StructureBlockInfo> basicBlocks, List<StructureTemplate.StructureBlockInfo> blocksWithEntities, List<StructureTemplate.StructureBlockInfo> specialBlocks) {}

    @Unique
    public void vs$fillFromVoxelSet(@NotNull Level level, @NotNull Iterable<BlockPos> voxels,
        @NotNull List<ServerShip> shipsBeingCopied, @NotNull Map<Long, Vector3d> centerPositions) {
        var minCorner = new BlockPos.MutableBlockPos(999999999, 999999999, 999999999);
        var maxCorner = new BlockPos.MutableBlockPos(-999999999, -999999999, -999999999);

        for (BlockPos pos: voxels) {
            minCorner.setX(Math.min(minCorner.getX(), pos.getX()));
            minCorner.setY(Math.min(minCorner.getY(), pos.getY()));
            minCorner.setZ(Math.min(minCorner.getZ(), pos.getZ()));

            maxCorner.setX(Math.max(maxCorner.getX(), pos.getX()));
            maxCorner.setY(Math.max(maxCorner.getY(), pos.getY()));
            maxCorner.setZ(Math.max(maxCorner.getZ(), pos.getZ()));
        }

        List<StructureTemplate.StructureBlockInfo> basicBlocks = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> blocksWithEntities = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> specialBlocks = Lists.newArrayList();

        for (BlockPos currentWorldPos : voxels) {
            BlockPos relativePos = currentWorldPos.subtract(minCorner);
            BlockState blockState = level.getBlockState(currentWorldPos);

            BlockEntity blockEntity = level.getBlockEntity(currentWorldPos);
            StructureTemplate.StructureBlockInfo blockInfo;

            Block block = blockState.getBlock();
            CompoundTag customTag = null;
            if (block instanceof ICopyableBlock) {
                customTag = ((ICopyableBlock) block).onCopy((ServerLevel) level, currentWorldPos, blockState, blockEntity, shipsBeingCopied, centerPositions);
            }

            if (customTag != null) {
                blockInfo = new StructureTemplate.StructureBlockInfo(relativePos, blockState, customTag);
            } else if (blockEntity != null) {
                blockInfo = new StructureTemplate.StructureBlockInfo(relativePos, blockState, blockEntity.saveWithId());
            } else {
                blockInfo = new StructureTemplate.StructureBlockInfo(relativePos, blockState, null);
            }

            addToLists(blockInfo, basicBlocks, blocksWithEntities, specialBlocks);
        }

        this.size = new Vec3i(
            maxCorner.getX() - minCorner.getX() + 1,
            maxCorner.getY() - minCorner.getY() + 1,
            maxCorner.getZ() - minCorner.getZ() + 1
        );

        List<StructureTemplate.StructureBlockInfo> finalBlockList = buildInfoList(basicBlocks, blocksWithEntities, specialBlocks);
        this.entityInfoList.clear();
        this.palettes.clear();
        this.palettes.add(PaletteInvoker.invokeInit(finalBlockList));
    }
}
