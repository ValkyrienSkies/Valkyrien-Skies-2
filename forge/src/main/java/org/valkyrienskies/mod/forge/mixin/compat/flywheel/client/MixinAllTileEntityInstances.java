package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.backend.instancing.InstanceData;
import com.jozufozu.flywheel.util.transform.MatrixTransformStack;
import com.jozufozu.flywheel.util.transform.TransformStack;
import com.jozufozu.flywheel.vanilla.ChestInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChestInstance.class)
public class MixinAllTileEntityInstances {

    @Unique
    private final List<InstanceData> instances = new ArrayList<>();

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/jozufozu/flywheel/util/transform/MatrixTransformStack;translate(Lnet/minecraft/core/Vec3i;)Lcom/jozufozu/flywheel/util/transform/TransformStack;"
        )
    )
    public TransformStack redirectTranslateTileEntity(final MatrixTransformStack receiver, final Vec3i tileEntityPos) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            final ShipObjectClient ship =
                VSGameUtilsKt.getShipObjectManagingPos(level, tileEntityPos);
            if (ship != null) {
                final ShipTransform transform = ship.getRenderTransform();
                VectorConversionsMCKt.multiply(
                    receiver.unwrap(),
                    transform.getShipToWorldMatrix(),
                    transform.getShipCoordinatesToWorldCoordinatesRotation()
                );
            }
        }
        return receiver;
    }
}
