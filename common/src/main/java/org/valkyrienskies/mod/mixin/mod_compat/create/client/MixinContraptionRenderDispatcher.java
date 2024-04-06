package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionRenderDispatcher;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(ContraptionRenderDispatcher.class)
public abstract class MixinContraptionRenderDispatcher {

    @Redirect(
            method = "renderActors",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/jozufozu/flywheel/util/transform/TransformStack;translate(Lnet/minecraft/core/Vec3i;)Ljava/lang/Object;"
            ), remap = false
    )
    private static Object redirectTranslate(final TransformStack instance, final Vec3i vec3i) {
        VSClientGameUtils.transformRenderIfInShipyard((PoseStack) instance, vec3i.getX(), vec3i.getY(), vec3i.getZ());
        return instance;
    }
}
