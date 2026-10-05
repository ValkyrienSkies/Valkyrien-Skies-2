package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.ShipCollisionQuery;

@Mixin(Player.class)
public abstract class MixinPlayer {
    @Unique
    private Vec3 vs$sneakingMovement;
    @Unique
    private ShipCollisionQuery vs$sneakingCollisions;

    @WrapMethod(method = "maybeBackOffFromEdge")
    private Vec3 vs$withSneakingCollisionQuery(final Vec3 movement, final MoverType moverType,
        final Operation<Vec3> original) {
        final Vec3 previousMovement = vs$sneakingMovement;
        final ShipCollisionQuery previousCollisions = vs$sneakingCollisions;
        vs$sneakingMovement = movement;
        vs$sneakingCollisions = null;
        try {
            // Keep vanilla's eligibility, X/Z/diagonal back-off and unchanged Y movement.
            // The query is only built if vanilla actually tests an unsupported position.
            return original.call(movement, moverType);
        } finally {
            vs$sneakingMovement = previousMovement;
            vs$sneakingCollisions = previousCollisions;
        }
    }

    @WrapOperation(method = {"maybeBackOffFromEdge", "isAboveGround"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean vs$includeShipSupport(final Level level, final Entity entity, final AABB box,
        final Operation<Boolean> original) {
        if (!original.call(level, entity, box)) {
            return false;
        }
        if (vs$sneakingMovement == null) {
            return true;
        }
        if (vs$sneakingCollisions == null) {
            // Covers all intermediate X/Z offsets and the below-feet isAboveGround probe.
            // One chunk/shape scan per move, shared by all of vanilla's 0.05-block steps.
            final AABB bounds = entity.getBoundingBox().expandTowards(
                vs$sneakingMovement.x, -entity.maxUpStep(), vs$sneakingMovement.z);
            vs$sneakingCollisions = EntityShipCollisionUtils.INSTANCE.createShipCollisionQuery(entity, bounds, level);
        }
        return !vs$sneakingCollisions.hasSupport(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
