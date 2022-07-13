package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.vanilla.ChestInstance;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChestInstance.class)
public class MixinAllTileEntityInstances {

//    @Unique
//    private final List<InstanceData> instances = new ArrayList<>();
//
//    @Redirect(
//        at = @At(
//            value = "INVOKE",
//            target = "Lcom/jozufozu/flywheel/util/transform/MatrixTransformStack;translate(Lnet/minecraft/core/Vec3i;)Lcom/jozufozu/flywheel/util/transform/TransformStack;"
//        )
//    )
//    public TransformStack redirectTranslateTileEntity(final MatrixTransformStack receiver, final Vec3i tileEntityPos) {
//        final ClientLevel level = Minecraft.getInstance().level;
//        if (level != null) {
//            final ShipObjectClient ship =
//                VSGameUtilsKt.getShipObjectManagingPos(level, tileEntityPos);
//            if (ship != null) {
//                final ShipTransform transform = ship.getRenderTransform();
//                VectorConversionsMCKt.multiply(
//                    receiver.unwrap(),
//                    transform.getShipToWorldMatrix(),
//                    transform.getShipCoordinatesToWorldCoordinatesRotation()
//                );
//            }
//        }
//        return receiver;
//    }
}
