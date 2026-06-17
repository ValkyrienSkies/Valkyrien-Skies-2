package org.valkyrienskies.mod.mixin.feature.ai.sleep;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// findStandUpPosition is invoked from stopSleeping (and player respawn) with a shipyard
// pos for ship-mounted beds; the returned Vec3 is shipyard-space, and the caller passes
// it straight to setPos — teleporting the waker ~10⁷ blocks away. Project to world.
@Mixin(BedBlock.class)
public abstract class MixinBedBlock {

    @ModifyReturnValue(method = "findStandUpPosition", at = @At("RETURN"))
    private static Optional<Vec3> vs$projectStandUpPosToWorld(
        final Optional<Vec3> result,
        @Local(argsOnly = true) final CollisionGetter levelArg
    ) {
        if (!result.isPresent()) return result;
        if (!(levelArg instanceof Level level)) return result;
        final Vec3 shipyard = result.get();
        final Vec3 world = VSGameUtilsKt.toWorldCoordinates(level, shipyard);
        // toWorldCoordinates returns the input by identity when no projection happens;
        // skip rewrapping the Optional in that case.
        if (world == shipyard) return result;
        return Optional.of(world);
    }

    // Vanilla searches an AABB around the shipyard bedPos and misses sleepers (whose world
    // position lives at the bed's projected center, set by MixinLivingEntitySleep). On a
    // densely-packed rotated ship multiple beds can project near the same world cell, so
    // filter the wider AABB hits by SLEEPING_POS to avoid cross-waking the wrong sleeper.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapOperation(
        method = "kickVillagerOutOfBed",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private List vs$kickAlsoSearchProjectedAabb(
        final Level level, final Class clazz, final AABB shipyardAabb, final Predicate predicate,
        final Operation<List> original,
        @Local(argsOnly = true) final BlockPos bedPos
    ) {
        final List<?> shipyardHits = original.call(level, clazz, shipyardAabb, predicate);
        if (!shipyardHits.isEmpty()) return shipyardHits;
        final LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, bedPos);
        if (ship == null) return shipyardHits;
        final Vector3d worldCenter = ship.getTransform().getShipToWorld().transformPosition(
            new Vector3d(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5),
            new Vector3d()
        );
        final AABB worldAabb = new AABB(
            worldCenter.x - 0.5, worldCenter.y - 0.5, worldCenter.z - 0.5,
            worldCenter.x + 0.5, worldCenter.y + 0.5, worldCenter.z + 0.5
        );
        final List<?> worldHits = original.call(level, clazz, worldAabb, predicate);
        final List<Object> matched = new ArrayList<>();
        for (final Object entity : worldHits) {
            if (entity instanceof LivingEntity living) {
                final Optional<BlockPos> sleepingPos = living.getSleepingPos();
                if (sleepingPos.isPresent() && sleepingPos.get().equals(bedPos)) {
                    matched.add(entity);
                }
            }
        }
        return matched;
    }
}
