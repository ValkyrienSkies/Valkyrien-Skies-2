package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.contraptions.components.millstone.MillstoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(MillstoneBlock.class)
public class MixinBlocks {

    @Redirect(
        method = "updateEntityAfterFallOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos redirectBlockPosition(final Entity entity) {
        final Vector3d pos = VSGameUtilsKt.getWorldCoordinates(entity.level, entity.blockPosition(),
            VectorConversionsMCKt.toJOMLD(entity.blockPosition()));
        return new BlockPos(pos.x, pos.y, pos.z);
    }

}
