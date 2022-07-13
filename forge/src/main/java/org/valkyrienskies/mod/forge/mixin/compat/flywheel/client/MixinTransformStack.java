package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.util.transform.TransformStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TransformStack.class)
public interface MixinTransformStack {

//    @Shadow
//    TransformStack translate(double v, double v1, double v2);
//
//    @Overwrite
//    default TransformStack translate(final Vec3i pos) {
//        final ClientLevel level = Minecraft.getInstance().level;
//        if (level != null) {
//            final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos);
//            if (ship != null) {
//                final ShipTransform transform = ship.getRenderTransform();
//                VectorConversionsMCKt.multiply(
//                    ((MatrixTransformStack) this).unwrap(),
//                    transform.getShipToWorldMatrix(),
//                    transform.getShipCoordinatesToWorldCoordinatesRotation()
//                );
//            }
//        }
//        return translate(pos.getX(), pos.getY(), pos.getZ());
//    }

}
