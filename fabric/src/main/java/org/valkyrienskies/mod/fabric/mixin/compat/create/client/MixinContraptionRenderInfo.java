package org.valkyrienskies.mod.fabric.mixin.compat.create.client;

/*
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ContraptionRenderInfo.class)
public class MixinContraptionRenderInfo {

    @Redirect(
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBoxForCulling()Lnet/minecraft/world/phys/AABB;"
            ),
            method = "beginFrame"
    )
    private AABB redirectGetAABBForCulling(final AbstractContraptionEntity receiver) {
        return VSGameUtilsKt.transformRenderAABBToWorld(((ClientLevel) receiver.level()), receiver.position(),
                receiver.getBoundingBoxForCulling());
    }
}
 */
