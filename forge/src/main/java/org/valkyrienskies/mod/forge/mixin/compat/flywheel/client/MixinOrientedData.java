package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.core.materials.OrientedData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(OrientedData.class)
public abstract class MixinOrientedData {

//    @Shadow
//    public abstract OrientedData setPosition(float x, float y, float z);
//
//    @Shadow
//    public abstract OrientedData setRotation(float x, float y, float z, float w);
//
//    @Overwrite
//    public OrientedData setPosition(final BlockPos pos) {
//        final ClientLevel level = Minecraft.getInstance().level;
//        if (level != null) {
//            final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos);
//            if (ship != null) {
//                final ShipTransform transform = ship.getRenderTransform();
//                final Vector3d inWorldPos = transform.getShipToWorldMatrix()
//                    .transformPosition(VectorConversionsMCKt.toJOMLD(pos));
//                final Quaterniondc rot = transform.getShipCoordinatesToWorldCoordinatesRotation();
//
//                setPosition((float) inWorldPos.x, (float) inWorldPos.y, (float) inWorldPos.z);
//                return setRotation((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w());
//            }
//        }
//
//        return setPosition(pos.getX(), pos.getY(), pos.getZ());
//    }

}
