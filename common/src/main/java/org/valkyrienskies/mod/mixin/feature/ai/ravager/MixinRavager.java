package org.valkyrienskies.mod.mixin.feature.ai.ravager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Ravager.aiStep iterates world cells in its inflated bbox to destroy leaves; for ship-mounted leaves the world cells are air, nothing destroyed, and the auto-jump fallback fires every tick (ravager pogos against the leaf wall). Before the auto-jump, also scan ship-local cells covered by the bbox and destroy any leaves found; suppress the jump if we destroyed something.
@Mixin(Ravager.class)
public abstract class MixinRavager {

    @WrapOperation(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/Ravager;jumpFromGround()V"
        )
    )
    private void vs$destroyShipLeavesInsteadOfJumping(
        final Ravager self, final Operation<Void> original
    ) {
        // Mirrors vanilla's "didn't auto-jump because I broke something" — skip the jump if we destroyed any ship-side leaves.
        if (vs$destroyShipSideLeaves()) return;
        original.call(self);
    }

    // Iterate every ship's projection of the ravager's inflated world bbox and destroy any LeavesBlock cells; returns true if anything was destroyed.
    @Unique
    private boolean vs$destroyShipSideLeaves() {
        final Ravager self = (Ravager) (Object) this;
        final Level level = self.level();
        final AABB worldBbox = self.getBoundingBox().inflate(0.2);
        final boolean[] destroyed = {false};
        VSGameUtilsKt.transformFromWorldToNearbyShips(level, worldBbox, shipBbox -> {
            final int minX = Mth.floor(shipBbox.minX);
            final int minY = Mth.floor(shipBbox.minY);
            final int minZ = Mth.floor(shipBbox.minZ);
            final int maxX = Mth.floor(shipBbox.maxX);
            final int maxY = Mth.floor(shipBbox.maxY);
            final int maxZ = Mth.floor(shipBbox.maxZ);
            for (final BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                final BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof LeavesBlock)) continue;
                if (level.destroyBlock(pos, true, self)) {
                    destroyed[0] = true;
                }
            }
        });
        return destroyed[0];
    }
}
