package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.simibubi.create.foundation.render.BlockEntityRenderHelper;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSClientGameUtils;

/**
 * This mixin is for Create 6.0.6
 * <br>
 * For the 6.0.7+ equivalent, see {@link MixinBlockEntityRenderHelper67}
 */
@Pseudo
@Mixin(value = BlockEntityRenderHelper.class, remap = false)
public abstract class MixinBlockEntityRenderHelper66 {
    @Redirect(
        method = "renderBlockEntities(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Ljava/lang/Iterable;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;F)V",
        at = @At(
            value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/core/Vec3i;)Ldev/engine_room/flywheel/lib/transform/Translate;"
        )
    )
    private static Translate redirectTranslate(PoseTransformStack instance, Vec3i vec3i) {
        VSClientGameUtils.transformRenderIfInShipyard(instance.unwrap(), vec3i.getX(), vec3i.getY(), vec3i.getZ());
        return instance;
    }
}
