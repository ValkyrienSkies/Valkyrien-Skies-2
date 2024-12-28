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
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    /**
     * This is necessary to avoid the vanilla flickering that occurs when entities are at high speeds.
     * <p>
     * Presumably, it is caused by the culling AABB only being updated on a subsequent tick, so we bypass that.
     * @param instance
     * @param original
     * @return
     */
    @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBoxForCulling()Lnet/minecraft/world/phys/AABB;"))
    private AABB redirectAABBConstructor(Entity instance, Operation<AABB> original) {
        if (instance instanceof IEntityDraggingInformationProvider dragProvider && dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
            EntityDraggingInformation dragInfo = dragProvider.getDraggingInformation();
            ClientShip ship = VSGameUtilsKt.getShipObjectWorld((ClientLevel) instance.level).getAllShips().getById(dragInfo.getLastShipStoodOn());
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
