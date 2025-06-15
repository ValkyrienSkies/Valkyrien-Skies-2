package org.valkyrienskies.mod.mixin.mod_compat.create.accessors;

import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ContraptionMatrices.class)
public interface ContraptionMatricesAccessor {
    @Invoker("setup")
    void invokeSetup(PoseStack viewProjection, AbstractContraptionEntity entity);
}
