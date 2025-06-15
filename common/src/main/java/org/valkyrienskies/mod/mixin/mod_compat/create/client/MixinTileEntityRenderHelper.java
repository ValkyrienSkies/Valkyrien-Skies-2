package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.foundation.render.BlockEntityRenderHelper;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(BlockEntityRenderHelper.class)
public abstract class MixinTileEntityRenderHelper {
    @Redirect(
        method = "renderBlockEntities(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Ljava/lang/Iterable;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;F)V",
        at = @At(
            value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/core/Vec3i;)Ldev/engine_room/flywheel/lib/transform/Translate;"
        )
    )
    private static Translate<PoseTransformStack> redirectTranslate(PoseTransformStack instance, Vec3i vec3i) {
        VSClientGameUtils.transformRenderIfInShipyard(instance.unwrap(), vec3i.getX(), vec3i.getY(), vec3i.getZ());
        return instance;
    }
}
