package org.valkyrienskies.mod.mixin.feature.ai.goal.panda;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Panda$PandaBreedGoal.canFindBamboo iterates a 3-tall × 16×16 cube around the panda's world blockPosition for Blocks.BAMBOO; for a panda on a ship next to ship-mounted bamboo the world cells are air, the search misses, and PandaBreedGoal.canUse flips the panda into the unhappy state with no recovery path.
@Mixin(targets = "net.minecraft.world.entity.animal.Panda$PandaBreedGoal")
public abstract class MixinPandaBreedGoalCanFindBamboo {

    @Shadow
    @Final
    private Panda panda;

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @Inject(method = "canFindBamboo", at = @At("HEAD"), cancellable = true)
    private void vs$canFindShipBamboo(final CallbackInfoReturnable<Boolean> cir) {
        final Level level = panda.level();
        final Ship ship = VSGameUtilsKt.getEnclosingShip(panda);
        if (ship == null) return;

        final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
            VS$IN.get().set(panda.getX(), panda.getY(), panda.getZ()), VS$OUT.get()
        );
        final BlockPos seed = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Vanilla iteration: i in [0,3), j in [0,8), k in [-j,j], l in {±j} or [-j,j] when |k| == j.
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 8; j++) {
                for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                    for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                        cursor.setWithOffset(seed, k, i, l);
                        if (level.getBlockState(cursor).is(Blocks.BAMBOO)) {
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }
    }
}
