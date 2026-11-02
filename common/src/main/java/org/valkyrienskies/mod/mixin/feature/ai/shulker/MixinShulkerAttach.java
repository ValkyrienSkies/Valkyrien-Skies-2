package org.valkyrienskies.mod.mixin.feature.ai.shulker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// Make Shulker attach/teleport ship-aware: world-block reads in canStayAt/isPositionBlocked/teleportSomewhere project into each candidate ship; the noCollision face-validation in canStayAt/teleportSomewhere routes through ShipAwareCollisionUtil so the bbox is rejected against ship geometry too.
@Mixin(Shulker.class)
public abstract class MixinShulkerAttach {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "canStayAt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;loadedAndEntityCanStandOnFace(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Direction;)Z"
        )
    )
    private boolean vs$canStandOnFaceIncludingShips(
        final Level level, final BlockPos worldPos, final Entity entity, final Direction face,
        final Operation<Boolean> original
    ) {
        if (original.call(level, worldPos, entity, face)) return true;
        for (final BlockPos shipLocal : vs$projectionsToCandidateShips(worldPos)) {
            if (original.call(level, shipLocal, entity, face)) return true;
        }
        return false;
    }

    @WrapOperation(
        method = "isPositionBlocked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$getBlockStateIncludingShips(
        final Level level, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(level, worldPos);
        if (worldState.isCollisionShapeFullBlock(level, worldPos)) return worldState;
        for (final BlockPos shipLocal : vs$projectionsToCandidateShips(worldPos)) {
            final BlockState shipState = original.call(level, shipLocal);
            if (shipState.isCollisionShapeFullBlock(level, shipLocal)) return shipState;
        }
        return worldState;
    }

    @WrapOperation(
        method = "teleportSomewhere",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean vs$isEmptyBlockIncludingShips(
        final Level level, final BlockPos worldPos, final Operation<Boolean> original
    ) {
        if (!original.call(level, worldPos)) return false;
        for (final BlockPos shipLocal : vs$projectionsToCandidateShips(worldPos)) {
            if (!original.call(level, shipLocal)) return false;
        }
        return true;
    }

    @WrapOperation(
        method = {"canStayAt", "teleportSomewhere"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean vs$noCollisionIncludingShips(
        final Level level, final Entity entity, final AABB aabb,
        final Operation<Boolean> original
    ) {
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, entity, aabb);
    }

    // Dragger first (precise authority for "currently attached"), then AABB-intersect scan (covers teleport candidates near a ship the shulker isn't currently attached to).
    @Unique
    private List<BlockPos> vs$projectionsToCandidateShips(final BlockPos worldPos) {
        final Shulker self = (Shulker) (Object) this;
        final Level level = self.level();
        final Vector3d worldCenter = VS$IN.get().set(
            worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
        final List<BlockPos> out = new ArrayList<>(2);

        final Ship dragShip = VSGameUtilsKt.getEnclosingShip(self);
        Long draggerShipId = null;
        if (dragShip != null) {
            draggerShipId = dragShip.getId();
            out.add(vs$project(dragShip, worldCenter));
        }

        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (draggerShipId != null && ship.getId() == draggerShipId.longValue()) continue;
            out.add(vs$project(ship, worldCenter));
        }

        return out;
    }

    @Unique
    private static BlockPos vs$project(final Ship ship, final Vector3d worldCenter) {
        final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
            worldCenter, VS$OUT.get()
        );
        return BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
    }
}
