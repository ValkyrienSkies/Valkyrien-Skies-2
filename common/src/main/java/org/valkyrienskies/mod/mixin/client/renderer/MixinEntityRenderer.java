package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    /**
     * This is necessary to avoid the vanilla flickering that occurs when entities are at high speeds.
     * <p>
     * Presumably, it is caused by the culling AABB only being updated on a subsequent tick, so we bypass that.
     */
    @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBoxForCulling()Lnet/minecraft/world/phys/AABB;"))
    private AABB redirectAABBConstructor(Entity instance, Operation<AABB> original) {
        // Mount relationship is authoritative: if the entity is ridden-on / sleeping-in a
        // ship-mounted vehicle/bed, MixinGameRenderer renders it via the ship's render
        // transform * mount pos. Use the same projection for the cull AABB so the entity
        // doesn't get culled at its stale stored bounding box.
        final ShipMountedToData mountedTo = VSGameUtilsKt.getShipMountedToData(instance, null);
        if (mountedTo != null) {
            final ClientShip mountShip = (ClientShip) mountedTo.getShipMountedTo();
            final Vector3dc mountWorld = mountShip.getRenderTransform().getShipToWorld()
                .transformPosition(mountedTo.getMountPosInShip(), new Vector3d());
            return instance.getDimensions(instance.getPose())
                .makeBoundingBox(mountWorld.x(), mountWorld.y(), mountWorld.z()).inflate(0.5D);
        }
        if (instance instanceof IEntityDraggingInformationProvider dragProvider && dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip() && dragProvider.getDraggingInformation().getLastShipStoodOn() != null) {
            EntityDraggingInformation dragInfo = dragProvider.getDraggingInformation();
            ClientShip ship = VSGameUtilsKt.getShipObjectWorld((ClientLevel) instance.level()).getAllShips().getById(dragInfo.getLastShipStoodOn());
            if (ship == null) {
                return original.call(instance);
            }
            if (dragInfo.getLastShipStoodOn() != null && (dragInfo.getRelativePositionOnShip() != null || dragInfo.getServerRelativePlayerPosition() != null)) {
                Vector3dc positionToTransform = dragInfo.bestRelativeEntityPosition();
                if (positionToTransform != null) {
                    Vector3dc transformed = ship.getRenderTransform().getShipToWorld().transformPosition(positionToTransform,
                        new Vector3d());
                    return instance.getDimensions(instance.getPose()).makeBoundingBox(transformed.x(), transformed.y(), transformed.z()).inflate(0.5D);
                }
            }
        }
        return original.call(instance);
    }
}
