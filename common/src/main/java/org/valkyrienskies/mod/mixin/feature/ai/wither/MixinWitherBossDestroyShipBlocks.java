package org.valkyrienskies.mod.mixin.feature.ai.wither;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.ShipBlockQueries;

// WitherBoss's 3x4x3 destroy loop only fires when destroyBlocksTick decrements to 0, and that's only set to 20 by hurt() — most reliably from Entity.isInWall suffocation. For ship walls, isInWall reads the WORLD cell at the wither's eye (air at the apparent ship location), so the wither never registers as suffocating in a ship wall and never triggers destruction. Same per-cell-clear ThreadLocal pattern as MixinEnderDragonCheckWalls.
@Mixin(WitherBoss.class)
public abstract class MixinWitherBossDestroyShipBlocks {

    @Shadow
    private int destroyBlocksTick;

    @Unique
    private static final ThreadLocal<BlockPos> vs$shipLocalPosForDestroy = new ThreadLocal<>();

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void vs$triggerDestroyOnShipCollision(final CallbackInfo ci) {
        final WitherBoss self = (WitherBoss) (Object) this;
        if (self.getInvulnerableTicks() > 0) return;
        if (this.destroyBlocksTick > 0) return;
        final Level level = self.level();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        if (vs$anyShipBlockInDestructionRange(self, level)) {
            this.destroyBlocksTick = 20;
        }
    }

    @Unique
    private static boolean vs$anyShipBlockInDestructionRange(final WitherBoss self, final Level level) {
        final int x0 = (int) Math.floor(self.getX());
        final int y0 = (int) Math.floor(self.getY());
        final int z0 = (int) Math.floor(self.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    if (vs$shipHasDestructibleAt(level, x0 + dx, y0 + dy, z0 + dz)) return true;
                }
            }
        }
        return false;
    }

    @Unique
    private static boolean vs$shipHasDestructibleAt(final Level level, final int wx, final int wy, final int wz) {
        return ShipBlockQueries.findShipBlockPos(level, new BlockPos(wx, wy, wz), WitherBoss::canDestroy) != null;
    }

    @WrapOperation(
        method = "customServerAiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateIncludingShips(
        final Level instance, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        vs$shipLocalPosForDestroy.remove();
        final BlockState worldState = original.call(instance, worldPos);
        if (WitherBoss.canDestroy(worldState)) return worldState;
        final BlockPos shipLocalPos = ShipBlockQueries.findShipBlockPos(instance, worldPos, WitherBoss::canDestroy);
        if (shipLocalPos != null) {
            vs$shipLocalPosForDestroy.set(shipLocalPos);
            return instance.getBlockState(shipLocalPos);
        }
        return worldState;
    }

    @WrapOperation(
        method = "customServerAiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean vs$destroyBlockAtShipPosIfApplicable(
        final Level instance, final BlockPos worldPos, final boolean dropBlock, final Entity entity,
        final Operation<Boolean> original
    ) {
        final BlockPos shipPos = vs$shipLocalPosForDestroy.get();
        vs$shipLocalPosForDestroy.remove();
        if (shipPos != null) {
            return original.call(instance, shipPos, dropBlock, entity);
        }
        return original.call(instance, worldPos, dropBlock, entity);
    }
}
