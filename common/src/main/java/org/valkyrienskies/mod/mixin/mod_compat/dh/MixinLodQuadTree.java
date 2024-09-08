package org.valkyrienskies.mod.mixin.mod_compat.dh;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.LodQuadTree;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.DhCompat;

@Mixin(LodQuadTree.class)
public class MixinLodQuadTree {

    @ModifyVariable(method = "calculateExpectedDetailLevel", at = @At("HEAD"), argsOnly = true, remap = false)
    DhBlockPos2D calculateExpectedDetailLevel_arg0(final DhBlockPos2D playerPos) {
        final var level = Minecraft.getInstance().level;

        final var worldPlayerPos = VSGameUtilsKt.toWorldCoordinates(level, playerPos.x, 0, playerPos.z);
        return new DhBlockPos2D((int) worldPlayerPos.x, (int) worldPlayerPos.z);
    }

    @ModifyVariable(method = "calculateExpectedDetailLevel", at = @At("HEAD"), argsOnly = true, remap = false)
    long calculateExpectedDetailLevel_arg1(final long sectionPos) {
        final var lod = DhSectionPos.getDetailLevel(sectionPos);
        final var world = DhCompat.toWorld(sectionPos);
        return DhSectionPos.encodeContaining(lod, new DhChunkPos(world.x, world.z));
    }

}
