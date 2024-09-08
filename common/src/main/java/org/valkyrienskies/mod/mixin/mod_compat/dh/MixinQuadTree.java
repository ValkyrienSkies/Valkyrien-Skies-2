package org.valkyrienskies.mod.mixin.mod_compat.dh;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.compat.DhCompat;

@Mixin(QuadTree.class)
public class MixinQuadTree {

    @ModifyVariable(method = "isSectionPosInBounds", at = @At("HEAD"), remap = false, argsOnly = true)
    long isSectionPosInBounds(final long testPos) {
        final var world = DhCompat.toWorld(testPos);
        final var detail = DhSectionPos.getDetailLevel(testPos);
        return DhSectionPos.encodeContaining(detail, new DhChunkPos(world.x, world.z));
    }

}
