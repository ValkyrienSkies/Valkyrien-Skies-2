package org.valkyrienskies.mod.mixin.mod_compat.dh;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.util.ArrayList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SharedApi.class)
public class MixinSharedApiApplyChunkUpdate {

    /**
     * update the neighbor world chunks around (and below) a ship chunk
     * this mixin will cause the neighbourChunkList to contain all neighbors of the shipyard chunk and all neighbors of the corresponding world chunk
     * quoting DH source code: this is done so lighting changes are propagated correctly
     */

    @Inject(
        method = "applyChunkUpdate",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;"),
        remap = false,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void applyChunkUpdate(
        IChunkWrapper chunkWrapper, ILevelWrapper level, boolean updateNeighborChunks, CallbackInfo ci,
        AbstractDhWorld dhWorld, IDhLevel dhLevel, int currentQueueCount, int maxQueueCount,  ArrayList<IChunkWrapper> neighbourChunkList
    ) {
        assert updateNeighborChunks;

        final var oldCX = chunkWrapper.getChunkPos().getX();
        final var oldCZ = chunkWrapper.getChunkPos().getZ();

        final var levelW = (Level) level.getWrappedMcObject();

        if (VSGameUtilsKt.isChunkInShipyard(levelW, oldCX, oldCZ)) {
            final var world = VSGameUtilsKt.toWorldCoordinates(levelW, oldCX, oldCZ);

            for (int xOffset = -1; xOffset <= 1; ++xOffset) {
                for (int zOffset = -1; zOffset <= 1; ++zOffset) {
                    final var neighbourPos = new DhChunkPos(world.x + xOffset, world.z + zOffset);
                    final var neighbourChunk = dhLevel.getLevelWrapper().tryGetChunk(neighbourPos);
                    if (neighbourChunk != null) {
                        neighbourChunkList.add(neighbourChunk);
                    }
                }
            }
        }
    }

}
