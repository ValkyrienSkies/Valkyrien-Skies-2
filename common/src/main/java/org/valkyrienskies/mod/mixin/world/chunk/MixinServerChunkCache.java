package org.valkyrienskies.mod.mixin.world.chunk;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCache {

//    /**
//     * @author Potato, Zaafonin, Endal
//     * @reason This mixin is used to adjust the level count for chunks in the shipyard because we don't want to be generating 900 chunks no one will ever see.
//     */
//    @WrapOperation(method = "runUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"))
//    private int vs$levelCountClamp(int i, int j, int k, Operation<Integer> original, @Local(name = "l") long l) {
//        ChunkPos chunk = new ChunkPos(ChunkPos.getX(l), ChunkPos.getZ(l));
//        int adjustedLevelCount = k;
//        if (ValkyrienSkiesMod.getApi().getServerShipWorld().isChunkInShipyard(chunk.x, chunk.z, "minecraft:dimension:minecraft:overworld")) {
//            if (adjustedLevelCount == 46) return adjustedLevelCount;
//            adjustedLevelCount = 35;
//        }
//        return original.call(i, j, adjustedLevelCount);
//    }
}
