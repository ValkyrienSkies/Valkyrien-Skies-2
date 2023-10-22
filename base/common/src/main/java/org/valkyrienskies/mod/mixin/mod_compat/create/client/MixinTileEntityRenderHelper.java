package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(BlockEntityRenderHelper.class)
public abstract class MixinTileEntityRenderHelper {
    @Redirect(
            method = "renderBlockEntities(Lnet/minecraft/world/level/Level;Lcom/jozufozu/flywheel/core/virtual/VirtualRenderWorld;Ljava/lang/Iterable;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/jozufozu/flywheel/util/transform/TransformStack;translate(Lnet/minecraft/core/Vec3i;)Ljava/lang/Object;"
            )
    )
    private static Object redirectTranslate(final TransformStack instance, final Vec3i vec3i) {
        VSClientGameUtils.transformRenderIfInShipyard((PoseStack) instance, vec3i.getX(), vec3i.getY(), vec3i.getZ());
        return instance;
    }
}
