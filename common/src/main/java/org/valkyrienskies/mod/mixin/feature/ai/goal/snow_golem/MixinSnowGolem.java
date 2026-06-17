package org.valkyrienskies.mod.mixin.feature.ai.goal.snow_golem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Snow Golem leaves a snow trail by placing Blocks.SNOW in 4 cells around its feet, gated on Snow.canSurvive(level, worldPos.below()) — for a ship-mounted golem on a grass deck the world cell below is air so the survival check fails. Run a parallel ship-aware placement at HEAD: project each candidate to ship-local and place there. Vanilla's path remains untouched (its checks fail for ship golems → no double placement; off-ship golems skip the inject).
@Mixin(SnowGolem.class)
public abstract class MixinSnowGolem {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void vs$shipAwareSnowTrail(final CallbackInfo ci) {
        final SnowGolem golem = (SnowGolem) (Object) this;
        final Level level = golem.level();
        if (level.isClientSide()) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;

        final Ship ship = VSGameUtilsKt.getEnclosingShip(golem);
        if (ship == null) return;

        final BlockState snow = Blocks.SNOW.defaultBlockState();
        for (int i = 0; i < 4; i++) {
            final double worldX = golem.getX() + (i % 2 * 2 - 1) * 0.25;
            final double worldY = golem.getY();
            final double worldZ = golem.getZ() + (i / 2 % 2 * 2 - 1) * 0.25;
            final Vector3d shipyardVec = ship.getTransform().getWorldToShip().transformPosition(
                VS$IN.get().set(worldX, worldY, worldZ), VS$OUT.get()
            );
            final BlockPos shipyardPos = BlockPos.containing(shipyardVec.x, shipyardVec.y, shipyardVec.z);
            if (level.isEmptyBlock(shipyardPos) && snow.canSurvive(level, shipyardPos)) {
                level.setBlockAndUpdate(shipyardPos, snow);
                level.gameEvent(GameEvent.BLOCK_PLACE, shipyardPos,
                    GameEvent.Context.of(golem, snow));
            }
        }
    }
}
