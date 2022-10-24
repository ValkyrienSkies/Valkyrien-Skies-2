package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.contraptions.components.millstone.MillstoneBlock;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.Ship;
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
        final Iterator<Ship> ships =
            VSGameUtilsKt.getShipsIntersecting(entity.level, entity.getBoundingBox()).iterator();
        if (ships.hasNext()) {
            final Vector3d pos = ships.next().getWorldToShip()
                .transformPosition(VectorConversionsMCKt.toJOMLD(entity.blockPosition()).add(0.5, 0.5, 0.5));
            return new BlockPos(Math.floor(pos.x), Math.floor(pos.y), Math.floor(pos.z));
        } else {
            return entity.blockPosition();
        }
    }

}
