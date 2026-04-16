package org.valkyrienskies.mod.mixin.feature.physics_block_entities;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.util.PhysicsBlockEntityUtil;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {
    @Shadow
    @Final
    Level level;

    @Inject(method = "updateBlockEntityTicker", at = @At("HEAD"))
    private <T extends BlockEntity> void beforeUpdateBlockEntityTicker(T blockEntity, CallbackInfo ci) {
        String dimensionId = ValkyrienSkies.getDimensionId(this.level);
        if (blockEntity instanceof BlockEntityPhysicsListener listener) {
            listener.setDimension(dimensionId);
            PhysicsBlockEntityUtil.onLoad(listener, blockEntity.getBlockPos(), this.level, "[ADDED] updateBlockEntityTicker");
        } else {
            //ValkyrienSkiesMod.INSTANCE.removeBlockEntityPhysTicker(blockEntity.getBlockPos(), dimensionId);
            PhysicsBlockEntityUtil.onRemove(blockEntity.getBlockPos(), this.level, "[REMOVED] updateBlockEntityTicker");
        }
    }

    // Inject into HEAD will not work
    @WrapOperation(method = "clearAllBlockEntities", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"))
    private void beforeClearAllBlockEntities(Map<BlockPos, BlockEntity> blockEntities, Operation<Void> original) {
        blockEntities.forEach((blockPos, blockEntity) -> {
            if (blockEntity instanceof BlockEntityPhysicsListener listener) {
                PhysicsBlockEntityUtil.onRemove(blockPos, this.level, "clearAllBlockEntities");
            }
        });
        original.call(blockEntities);
    }

    @Inject(method = "removeBlockEntity", at = @At("TAIL"))
    private void afterRemoveBlockEntity(BlockPos blockPos, CallbackInfo ci) {
        PhysicsBlockEntityUtil.onRemove(blockPos, this.level, "removeBlockEntity");
    }
}
