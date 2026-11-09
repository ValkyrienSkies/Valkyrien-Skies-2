package org.valkyrienskies.mod.mixin.feature.ai.phantom;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Phantom;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Phantom.finalizeSpawn sets anchorPoint = blockPosition().above(5); for a phantom spawned at a shipyard pos (spawn egg on a ship block, summon, mob spawner in shipyard chunk) the anchor would be shipyard coords and PhantomCircleAroundAnchorGoal would fly toward the shipyard origin millions of blocks away.
@Mixin(Phantom.class)
public abstract class MixinPhantom {

    @ModifyExpressionValue(
        method = "finalizeSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;above(I)Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$projectAnchorToWorld(final BlockPos anchor) {
        final Phantom self = (Phantom) (Object) this;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(self.level(), anchor);
        if (ship == null) return anchor;
        final Vector3d worldPos = ship.getShipToWorld().transformPosition(
            new Vector3d(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5),
            new Vector3d()
        );
        return BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
    }
}
