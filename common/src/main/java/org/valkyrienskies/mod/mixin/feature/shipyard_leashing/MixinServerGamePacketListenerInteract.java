package org.valkyrienskies.mod.mixin.feature.shipyard_leashing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// handleInteract gates entity interactions on bbox.distanceToSqr(playerEye); for shipyard-frame entities (leash-fence knot on a ship-mounted fence, armor stand / item frame / painting in shipyard) the bbox lives at ~10⁷ and the server silently drops every interaction even though the client raycast already dispatched it. Project the bbox to world for the reach check.
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerInteract {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "handleInteract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
        )
    )
    private AABB vs$projectShipyardBboxForReachCheck(
        final Entity targetEntity, final Operation<AABB> original
    ) {
        final AABB raw = original.call(targetEntity);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(targetEntity.level(), targetEntity.position());
        if (ship == null) return raw;
        // Project all 8 corners of the bbox; for a rotated ship the 2-corner shortcut would collapse the world AABB on some axes.
        final Matrix4dc s2w = ship.getTransform().getShipToWorld();
        final Vector3d in = VS$IN.get();
        final Vector3d out = VS$OUT.get();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            in.set(
                (i & 1) != 0 ? raw.maxX : raw.minX,
                (i & 2) != 0 ? raw.maxY : raw.minY,
                (i & 4) != 0 ? raw.maxZ : raw.minZ
            );
            s2w.transformPosition(in, out);
            if (out.x < minX) minX = out.x;  if (out.x > maxX) maxX = out.x;
            if (out.y < minY) minY = out.y;  if (out.y > maxY) maxY = out.y;
            if (out.z < minZ) minZ = out.z;  if (out.z > maxZ) maxZ = out.z;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
