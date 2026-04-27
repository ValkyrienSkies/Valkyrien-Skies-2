package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
    @WrapOperation(
        method = "renderBlockEntities",
        at = @At(
            value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/core/Vec3i;)Ldev/engine_room/flywheel/lib/transform/Translate;"
        ),
        remap = false
    )
    private static Translate<PoseTransformStack> redirectTranslate(PoseTransformStack instance, Vec3i vec3i, Operation<PoseTransformStack> original) {
        VSClientGameUtils.transformRenderIfInShipyard(instance.unwrap(), vec3i.getX(), vec3i.getY(), vec3i.getZ(), original);
        return instance;
    }
}
