package org.valkyrienskies.mod.mixin.feature.structure_template;

import com.google.common.collect.Lists;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.assembly.ICopyableBlock;
import org.valkyrienskies.mod.util.StructureTemplateFillFromVoxelSet;

@Mixin(value = StructureTemplate.class)
public abstract class StructureTemplateMixin implements StructureTemplateFillFromVoxelSet {

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
        @NotNull List<ServerShip> shipsBeingCopied, @NotNull Map<Long, Vector3d> centerPositions,
        @NotNull BlockPos min, @NotNull BlockPos max) {

        List<StructureTemplate.StructureBlockInfo> basicBlocks = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> blocksWithEntities = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> specialBlocks = Lists.newArrayList();

        for (BlockPos currentWorldPos : voxels) {
            BlockPos relativePos = currentWorldPos.subtract(min);
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
            max.getX() - min.getX() + 1,
            max.getY() - min.getY() + 1,
            max.getZ() - min.getZ() + 1
        );

        List<StructureTemplate.StructureBlockInfo> finalBlockList = buildInfoList(basicBlocks, blocksWithEntities, specialBlocks);
        this.palettes.clear();
        this.palettes.add(PaletteInvoker.invokeInit(finalBlockList));

        this.vs$fillEntities(level, min, max);
    }

    @Unique
    private void vs$fillEntities(
        Level level,
        BlockPos min,
        BlockPos max
    ) {
        this.entityInfoList.clear();

        AABB bounds = new AABB(
            min.getX(), min.getY(), min.getZ(),
            max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0
        );

        for (Entity entity : level.getEntitiesOfClass(
            Entity.class,
            bounds,
            e -> !e.isPassenger() && !(e instanceof net.minecraft.world.entity.player.Player)
        )) {
            // Position relative to the copied structure's minimum corner.
            Vec3 relativePos = entity.position().subtract(
                min.getX(),
                min.getY(),
                min.getZ()
            );

            BlockPos relativeBlockPos = new BlockPos(
                Mth.floor(relativePos.x),
                Mth.floor(relativePos.y),
                Mth.floor(relativePos.z)
            );

            CompoundTag entityNbt = new CompoundTag();
            entity.saveWithoutId(entityNbt);
            entityNbt.putString(
                "id",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
            );

            this.entityInfoList.add(
                new StructureTemplate.StructureEntityInfo(
                    relativePos,
                    relativeBlockPos,
                    entityNbt
                )
            );
        }
    }
}
