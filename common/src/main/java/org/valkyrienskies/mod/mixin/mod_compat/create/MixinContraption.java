package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.impl.config.VSCoreConfig;
import org.valkyrienskies.mod.compat.create.ContraptionSegmentHelper;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mixin(Contraption.class)
public class MixinContraption {
    @Shadow
    public BlockPos anchor;

    @Shadow
    protected Map<BlockPos, StructureBlockInfo> blocks;

    @WrapMethod(method = "removeBlocksFromWorld")
    private void vs$removeBlocksWithoutQueuingSplits(Level level, BlockPos offset, Operation<Void> original) {
        if (!(level instanceof ServerLevel serverLevel) || blocks.isEmpty()
            || !VSCoreConfig.SERVER.getSp().getEnableSplitting()) {
            original.call(level, offset);
            return;
        }

        BlockPos origin = anchor.offset(offset);
        Set<Long> visitedChunks = new HashSet<>();
        Set<Long> shipIds = new HashSet<>();
        for (StructureBlockInfo block : blocks.values()) {
            BlockPos pos = block.pos().offset(origin);
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (visitedChunks.add(ChunkPos.asLong(chunkX, chunkZ))) {
                Ship ship = VSGameUtilsKt.getShipManagingPos(level, chunkX, chunkZ);
                if (ship != null) shipIds.add(ship.getId());
            }
        }

        ValkyrienSkiesMod.getSplitHandler().withoutQueuingSplits(serverLevel, shipIds,
            () -> original.call(level, offset));
    }

    @Redirect(method = "onEntityCreated", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean wrapOp(Level level, Entity entity) {
        // BlockPos anchor = blockFace.getConnectedPos();
        // movedContraption.setPos(anchor.getX() + .5f, anchor.getY(), anchor.getZ() + .5f);
        //
        // Derive anchor from the code above
        final BlockPos anchor = BlockPos.containing((int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ()));
        boolean added = level.addFreshEntity(entity);
        if (added) {
            entity.moveTo(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        }
        return added;
    }

    @Inject(method = "onEntityCreated", at = @At("HEAD"), remap = false)
    private void preOnEntityCreated(AbstractContraptionEntity entity, CallbackInfo ci) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            ContraptionSegmentHelper.ensureContraptionSegment(serverLevel, entity);
        }
    }
}
