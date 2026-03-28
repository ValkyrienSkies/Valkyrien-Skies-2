package org.valkyrienskies.mod.mixin.mod_compat.alex_caves;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.impl.config.VSCoreConfig;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(Entity.class)
public abstract class MixinNuclearExplosion {

    @Unique
    private static final String VS2$ALEX_CAVES_NUKE_CLASS =
        "com.github.alexmodguy.alexscaves.server.entity.item.NuclearExplosionEntity";

    @Unique
    private boolean vs2$forcesApplied = false;

    @Unique
    private final Set<Long> vs2$affectedShips = new HashSet<>();

    @Unique
    private void vs2$applyNuclearForces() {
        if (vs2$forcesApplied) {
            return;
        }

        Entity nuke = (Entity) (Object) this;

        if (!nuke.getClass().getName().equals(VS2$ALEX_CAVES_NUKE_CLASS)) {
            return;
        }

        final Level entityLevel = nuke.level();
        if (!(entityLevel instanceof ServerLevel level)) {
            return;
        }

        vs2$forcesApplied = true;

        try {
            // Get explosion properties using reflection
            double x = nuke.getX();
            double y = nuke.getY();
            double z = nuke.getZ();
            
            // Reflection: call getSize() via method on the entity
            var getSizeMethod = nuke.getClass().getDeclaredMethod("getSize");
            getSizeMethod.setAccessible(true);
            float size = (float) getSizeMethod.invoke(nuke);

            // Calculate radius: chunksAffected * 15, where chunksAffected = ceil(size)
            int chunksAffected = (int) Math.ceil(size);
            float radius = chunksAffected * 15;

            // Custom forces similar to vanilla explosion
            final Vector3d originPos = new Vector3d(x, y, z);
            final BlockPos explodePos = BlockPos.containing(originPos.x(), originPos.y(), originPos.z());
            final int radiusInt = (int) Math.ceil(radius);

            for (int dx = radiusInt; dx >= -radiusInt; dx--) {
                for (int dy = radiusInt; dy >= -radiusInt; dy--) {
                    for (int dz = radiusInt; dz >= -radiusInt; dz--) {
                        final BlockHitResult result = level.clip(
                            new ClipContext(Vec3.atCenterOf(explodePos),
                                Vec3.atCenterOf(explodePos.offset(dx, dy, dz)),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE, null));
                        if (result.getType() == Type.BLOCK) {
                            final BlockPos blockPos = result.getBlockPos();
                            final LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, blockPos);
                            if (ship != null && !vs2$affectedShips.contains(ship.getId())) {
                                vs2$affectedShips.add(ship.getId());

                                final Vector3d forceVector =
                                    VectorConversionsMCKt.toJOML(
                                        Vec3.atCenterOf(explodePos)); // Start at center position
                                final Double distanceMult = Math.max(0.5, 1.0 - (radius /
                                    forceVector.distance(VectorConversionsMCKt.toJOML(Vec3.atCenterOf(blockPos)))));
                                final Double powerMult = Math.max(0.1, radius / 4); // TNT blast radius = 4

                                forceVector.sub(VectorConversionsMCKt.toJOML(
                                    Vec3.atCenterOf(blockPos))); // Subtract hit block pos to get direction
                                forceVector.normalize();
                                forceVector.mul(-1 *
                                    VSGameConfig.SERVER.getExplosionBlastForce()); // Multiply by blast force
                                forceVector.mul(distanceMult); // Multiply by distance falloff
                                forceVector.mul(powerMult); // Multiply by radius

                                Vector3dc modelPos = VectorConversionsMCKt.toJOML(Vec3.atCenterOf(blockPos));

                                if (VSCoreConfig.SERVER.getSp().getEnableSplitting()) {
                                    // custom split logic for nukes
                                    ValkyrienSkiesMod.splitHandler.split(level, ship.getId(), (ServerShip ship1) ->
                                        ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension()).applyWorldForceToModelPos(
                                            ship1.getId(), forceVector, ship1.getTransform().getWorldToShip().transformPosition(
                                                ship.getTransform().getShipToWorld().transformPosition(modelPos, new Vector3d())))
                                    );
                                }

                                final GameToPhysicsAdapter forceApplier = ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension());
                                if (forceVector.isFinite()) {
                                    forceApplier.applyWorldForceToModelPos(ship.getId(), forceVector,
                                        VectorConversionsMCKt.toJOML(Vec3.atCenterOf(blockPos)));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail if something goes wrong (likely running without Alex Caves mod)
        }
    }

    @Inject(at = @At("HEAD"), method = "tick")
    private void onNuclearTick(final CallbackInfo ci) {
        vs2$applyNuclearForces();
    }
}
