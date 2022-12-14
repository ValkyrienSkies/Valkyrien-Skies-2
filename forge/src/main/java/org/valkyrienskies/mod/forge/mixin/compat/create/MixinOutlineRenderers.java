package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.tileEntity.behaviour.ValueBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(ValueBox.class)
public class MixinOutlineRenderers {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"
        ),
        require = 0
    )
    public void redirectTranslate(final PoseStack instance, final double x, final double y, final double z) {
        VSClientGameUtils.transformRenderIfInShipyard(instance, x, y, z);
    }

}
