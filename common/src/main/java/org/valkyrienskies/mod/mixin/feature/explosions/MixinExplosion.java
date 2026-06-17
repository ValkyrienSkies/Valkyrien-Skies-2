package org.valkyrienskies.mod.mixin.feature.explosions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.impl.config.VSCoreConfig;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;

@Mixin(Explosion.class)
public abstract class MixinExplosion {

    @Shadow
    @Final
    private Level level;
    @Shadow
    @Final
    @Mutable
    private double x;

    @Shadow
    @Final
    @Mutable
    private double y;
    @Shadow
    @Final
    @Mutable
    private double z;
    @Shadow
    @Final
    @Mutable
    private float radius;

    @Unique
    private List<Ship> vs2$intersectingShips = Collections.emptyList();
    @Unique
    private final Vector3d vs2$tmpShipPos = new Vector3d();

    @Shadow
    public abstract void explode();

    @Inject(method = "explode", at = @At("HEAD"))
    private void vs2$cacheIntersectingShips(final CallbackInfo ci) {
        if (this.level == null || this.radius <= 0.0F) {
            this.vs2$intersectingShips = Collections.emptyList();
            return;
        }
        final AABBd aabb = new AABBd(
            this.x - this.radius, this.y - this.radius, this.z - this.radius,
            this.x + this.radius, this.y + this.radius, this.z + this.radius
        );
        final List<Ship> ships = new ArrayList<>();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(this.level, aabb)) {
            ships.add(ship);
        }
        this.vs2$intersectingShips = ships;
    }

    @WrapOperation(
        method = "explode",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs2$frameAwareBlockPos(final double wx, final double wy, final double wz,
                                            final Operation<BlockPos> operation) {
        for (int i = 0, n = this.vs2$intersectingShips.size(); i < n; i++) {
            final Ship ship = this.vs2$intersectingShips.get(i);
            if (!ship.getWorldAABB().containsPoint(wx, wy, wz)) {
                continue;
            }
            this.vs2$tmpShipPos.set(wx, wy, wz);
            ship.getWorldToShip().transformPosition(this.vs2$tmpShipPos);
            final BlockPos shipBlockPos = BlockPos.containing(
                this.vs2$tmpShipPos.x, this.vs2$tmpShipPos.y, this.vs2$tmpShipPos.z
            );
            if (!this.level.getBlockState(shipBlockPos).isAir()) {
                return shipBlockPos;
            }
        }
        return operation.call(wx, wy, wz);
    }

    @Inject(method = "explode", at = @At("TAIL"))
    private void vs2$afterExplode(final CallbackInfo ci) {
        try {
            vs2$doExplodeForce();
        } finally {
            this.vs2$intersectingShips = Collections.emptyList();
        }
    }

    @Unique
    private void vs2$doExplodeForce() {
        if (this.level.isClientSide) {
            return;
        }
        final Vec3 origin = new Vec3(this.x, this.y, this.z);
        final int radius = (int) Math.ceil(this.radius);
        final double blastForce = VSGameConfig.SERVER.getExplosionBlastForce();
        final double powerMult = Math.max(0.1, this.radius / 4.0); // TNT blast radius = 4
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    final BlockHitResult result = this.level.clip(new ClipContext(
                        origin,
                        new Vec3(this.x + x, this.y + y, this.z + z),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        null));
                    if (result.getType() != Type.BLOCK) {
                        continue;
                    }
                    final LoadedServerShip ship =
                        VSGameUtilsKt.getLoadedShipManagingPos((ServerLevel) this.level, result.getBlockPos());
                    if (ship == null) {
                        continue;
                    }

                    // clipIncludeShips back-projects ship hits to world coordinates here.
                    final Vec3 hitWorld = result.getLocation();
                    final double dx = hitWorld.x - this.x;
                    final double dy = hitWorld.y - this.y;
                    final double dz = hitWorld.z - this.z;
                    final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance < 1.0e-9 || distance >= this.radius * 2.0) {
                        continue;
                    }
                    // Vanilla's entity knockback falloff: linear from 1.0 at center to 0.0 at 2*radius.
                    final double distanceMult = 1.0 - distance / (this.radius * 2.0);
                    final double scale = blastForce * powerMult * distanceMult / distance;
                    final Vector3d forceInWorld = new Vector3d(dx * scale, dy * scale, dz * scale);
                    if (!forceInWorld.isFinite()) {
                        continue;
                    }
                    final Vector3d posInWorld = new Vector3d(hitWorld.x, hitWorld.y, hitWorld.z);

                    final GameToPhysicsAdapter forceApplier =
                        ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension());
                    forceApplier.applyWorldForce(ship.getId(), forceInWorld, posInWorld);

                    if (VSCoreConfig.SERVER.getSp().getEnableSplitting()) {
                        // custom split logic for TNT specifically
                        ValkyrienSkiesMod.splitHandler.split(this.level, ship.getId(), (ServerShip split) ->
                            forceApplier.applyWorldForce(split.getId(), forceInWorld, posInWorld));
                    }
                }
            }
        }
    }

    @WrapOperation(
        method = "getSeenPercent",
        at = @At(
            value = "NEW",
            target = "Lnet/minecraft/world/level/ClipContext;"
        )
    )
    private static ClipContext getSeenPercent$ClipContext$new(
        Vec3 from,
        Vec3 to,
        final ClipContext.Block blockClip,
        final ClipContext.Fluid fluidClip,
        final Entity source,
        final Operation<ClipContext> operation
    ) {
        if (source != null) {
            final Level level = source.level();
            from = VSGameUtilsKt.toWorldCoordinates(level, from);
            to = VSGameUtilsKt.toWorldCoordinates(level, to);
        }
        return operation.call(from, to, blockClip, fluidClip, source);
    }
}
