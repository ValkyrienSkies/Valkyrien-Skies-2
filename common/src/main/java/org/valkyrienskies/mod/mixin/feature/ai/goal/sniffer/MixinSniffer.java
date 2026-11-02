package org.valkyrienskies.mod.mixin.feature.ai.goal.sniffer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Sniffer dig pipeline assumes integer-aligned terrain. Three breaks for ship-mounted sniffers: (1) canDig's getBlockState reads the world cell (air for ship blocks) — project to overlapping ships and check both the candidate and the cell above (deck blocks straddle two integer cells under fractional Y); (2) canDig's createPath fails because LandPathNavigation walks down from an air target looking for support and finds nothing under a floating ship — retry with the projected shipyard pos; (3) dropSeed spawns the loot ItemEntity at floor(snout.Y) which lands inside the deck block on fractional Y — use precise mob.getY() + 0.2 instead.
@Mixin(Sniffer.class)
public abstract class MixinSniffer {

    @Unique
    private static final ThreadLocal<Vector3d> VS$SAMPLE = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "canDig(Lnet/minecraft/core/BlockPos;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$shipAwareDiggableLookup(final Level level, final BlockPos worldPos,
        final Operation<BlockState> original) {
        final BlockState vanilla = original.call(level, worldPos);
        if (!vanilla.isAir()) return vanilla;

        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        final Vector3d sample = VS$SAMPLE.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            for (int dy = 0; dy <= 1; dy++) {
                sample.set(worldPos.getX() + 0.5, worldPos.getY() + 0.5 + dy, worldPos.getZ() + 0.5);
                if (!ship.getWorldAABB().containsPoint(sample)) continue;
                final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                    sample, VS$LOCAL.get()
                );
                final BlockPos shipyardPos = BlockPos.containing(local.x, local.y, local.z);
                final BlockState shipState = level.getBlockState(shipyardPos);
                if (!shipState.isAir()) return shipState;
            }
        }
        return vanilla;
    }

    @WrapOperation(
        method = "canDig(Lnet/minecraft/core/BlockPos;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;"
        )
    )
    private Path vs$shipAwarePathToDig(final PathNavigation nav, final BlockPos worldPos,
        final int accuracy, final Operation<Path> original) {
        final Path vanilla = original.call(nav, worldPos, accuracy);
        if (vanilla != null && vanilla.canReach()) return vanilla;

        final Sniffer sniffer = (Sniffer) (Object) this;
        final Level level = sniffer.level();
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        final Vector3d sample = VS$SAMPLE.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            for (int dy = 0; dy <= 1; dy++) {
                sample.set(worldPos.getX() + 0.5, worldPos.getY() + 0.5 + dy, worldPos.getZ() + 0.5);
                if (!ship.getWorldAABB().containsPoint(sample)) continue;
                final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                    sample, VS$LOCAL.get()
                );
                final BlockPos shipyardPos = BlockPos.containing(local.x, local.y, local.z);
                final Path shipPath = original.call(nav, shipyardPos, accuracy);
                if (shipPath != null && shipPath.canReach()) return shipPath;
            }
        }
        return vanilla;
    }

    @ModifyArg(
        method = "dropSeed",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"
        ),
        index = 2
    )
    private double vs$preciseSeedDropY(final double originalY) {
        return ((Sniffer) (Object) this).getY() + 0.2;
    }
}
