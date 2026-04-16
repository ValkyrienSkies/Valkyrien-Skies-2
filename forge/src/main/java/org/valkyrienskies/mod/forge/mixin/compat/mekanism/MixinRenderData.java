package org.valkyrienskies.mod.forge.mixin.compat.mekanism;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.client.render.data.RenderData;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderData.Builder.class, remap = false)
public class MixinRenderData {
    /**
     * The issue seems to not be present with latest VS and Mekanism. Still useful as a failsafe for old Mek versions.
     *
     * This is a temporary fix for https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/1252
     * which bypasses the fatal error (renderLocation not existing for multiblocks on client side) by calculating it
     * manually.<p>
     *
     * While preventing a crash this does not fix the culprit issue of renderLocation. My guess is desync between
     * server and client, as Mekanism code says renderLocation "may be null if structure has not been fully sent".
     * I've investigated into a possible incorrect check for chunk loadedness or player proximity but couldn't find
     * anything.<p>
     *
     * TODO: find why renderLocation is not present on multiblocks and why it only happens on shipyards.
     */
    @WrapOperation(
        method = "of(Lmekanism/common/lib/multiblock/MultiblockData;)Lmekanism/client/render/data/RenderData$Builder;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Objects;requireNonNull(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
        )
    )
    public Object createRenderLocation(
        Object renderLocation, String message, Operation<Object> original,
        @Local(argsOnly = true) MultiblockData multiblock) {
        if (renderLocation == null) {
            // Recalculate multiblock renderLocation as done in MultiblockData#setShape
            multiblock.renderLocation = multiblock.getBounds().getMinPos().relative(Direction.UP);
        }
        return original.call(multiblock.renderLocation, message);
    }
}
