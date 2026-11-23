package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Shadow;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.util.EntityRenderPosition;
import org.valkyrienskies.mod.common.world.RayQuery;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Shadow public abstract Vec3 getRenderOffset(Entity entity, float t);

    /**
     * This is necessary to avoid the vanilla flickering that occurs when entities are at high speeds.
     * <p>
     * Presumably, it is caused by the culling AABB only being updated on a subsequent tick, so we bypass that.
     */
    @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBoxForCulling()Lnet/minecraft/world/phys/AABB;"))
    private AABB redirectAABBConstructor(Entity instance, Operation<AABB> original) {
        if (RayQuery.frameTime() != null) {
            var pose = RayQuery.visualSeat(instance);
            AABB box = original.call(instance);
            return pose == null ? box : pose.bounds(box.move(-instance.getX(), -instance.getY(), -instance.getZ()));
        }
        // Mount relationship is authoritative: if the entity is ridden-on / sleeping-in a
        // ship-mounted vehicle/bed, MixinGameRenderer renders it via the ship's render
        // transform * mount pos. Use the same projection for the cull AABB so the entity
        // doesn't get culled at its stale stored bounding box.
        final ShipMountedToData mountedTo = VSGameUtilsKt.getShipMountedToData(instance, RayQuery.frameTime());
        if (mountedTo != null) {
            final ClientShip mountShip = (ClientShip) mountedTo.getShipMountedTo();
            final Vector3dc mountWorld = mountShip.getRenderTransform().getShipToWorld()
                .transformPosition(mountedTo.getMountPosInShip(), new Vector3d());
            // Preserve asymmetric/custom bounds about the model origin, then apply the same
            // linear part (including nonuniform scale) as EntityRenderDispatcher.
            Float t = RayQuery.frameTime();
            Vec3 offset = getRenderOffset(instance, t == null ? 1f : t);
            AABB local = original.call(instance).move(offset.x - instance.getX(), offset.y - instance.getY(), offset.z - instance.getZ());
            return EntityRenderPosition.seatedBounds(local, mountShip.getRenderTransform().getShipToWorld(), mountWorld); // vanilla adds its 0.5 margin
        }
        // A dragged entity already has its presented AABB for the duration of renderLevel.
        return original.call(instance);
    }
}
