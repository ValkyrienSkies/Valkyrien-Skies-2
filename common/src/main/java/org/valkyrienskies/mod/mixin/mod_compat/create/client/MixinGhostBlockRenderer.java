package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(targets = {"com.simibubi.create.foundation.utility.ghost.GhostBlockRenderer$DefaultGhostBlockRenderer",
        "com.simibubi.create.foundation.utility.ghost.GhostBlockRenderer$TransparentGhostBlockRenderer"})
public class MixinGhostBlockRenderer {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0)
    )
    private void redirectTranslate(
            final PoseStack instance, final double pose, final double d, final double e) {
        VSClientGameUtils.transformRenderIfInShipyard(instance, pose, d, e);
    }
}
