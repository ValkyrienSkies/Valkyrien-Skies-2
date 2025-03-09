package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(ContraptionEntityRenderer.class)
public abstract class MixinContraptionRenderDispatcher {

    @Redirect(
        method = "renderActors",
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
