package org.valkyrienskies.mod.mixin.feature.physics_block_entities;

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
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {
    @Shadow
    @Final
    Level level;

    @Shadow
    private boolean loaded;

    @Shadow
    public abstract Map<BlockPos, BlockEntity> getBlockEntities();

    @Inject(method = "updateBlockEntityTicker", at = @At("HEAD"))
    private <T extends BlockEntity> void onUpdateBlockEntityTickerHead(T blockEntity, CallbackInfo ci) {
        String dimensionId = ValkyrienSkies.getDimensionId(this.level);
        if (blockEntity instanceof BlockEntityPhysicsListener listener) {
            listener.setDimension(dimensionId);
            if (blockEntity.isRemoved()) {
                ValkyrienSkiesMod.INSTANCE.removeBlockEntityPhysTicker(blockEntity.getBlockPos(), dimensionId);
                return;
            }
            ValkyrienSkiesMod.INSTANCE.addBlockEntityPhysTicker(dimensionId, blockEntity.getBlockPos(), listener);

        } else {
            ValkyrienSkiesMod.INSTANCE.removeBlockEntityPhysTicker(blockEntity.getBlockPos(), dimensionId);
        }
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void onClearAllBlockEntitiesHead(CallbackInfo ci) {
        getBlockEntities().forEach((blockPos, blockEntity) -> {
            if (blockEntity instanceof BlockEntityPhysicsListener listener) {
                String dimensionId = ValkyrienSkies.getDimensionId(this.level);
                ValkyrienSkiesMod.INSTANCE.removeBlockEntityPhysTicker(blockPos, dimensionId);
            }
        });
    }

    @Inject(method = "removeBlockEntity", at = @At("TAIL"))
    private void onRemoveBlockEntityTickerHead(BlockPos blockPos, CallbackInfo ci) {
        if (!loaded) {
            return;
        }
        String dimensionId = ValkyrienSkies.getDimensionId(this.level);
        BlockEntity be = level.getBlockEntity(blockPos);
        if (be instanceof BlockEntityPhysicsListener && !be.isRemoved()) {
            return;
        }
        ValkyrienSkiesMod.INSTANCE.removeBlockEntityPhysTicker(blockPos, dimensionId);
    }
}
