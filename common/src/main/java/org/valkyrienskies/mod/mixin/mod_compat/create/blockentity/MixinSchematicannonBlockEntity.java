package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SchematicannonBlockEntity.class)
public class MixinSchematicannonBlockEntity {

    @Redirect(
        method = "initializePrinter",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/SchematicPrinter;getAnchor()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos redirectGetBlockPos(final SchematicPrinter instance) {
        SchematicannonBlockEntity thisBE = SchematicannonBlockEntity.class.cast(this);
        final BlockPos original = instance.getAnchor();
        final Ship thisShip = VSGameUtilsKt.getShipObjectManagingPos(thisBE.getLevel(), thisBE.getBlockPos());
        final Ship targetShip = VSGameUtilsKt.getShipObjectManagingPos(thisBE.getLevel(), original);

        // If we're on the same ship as where we're placing, don't change behaviour
        if (thisShip == targetShip) {
            return original;
        }

        Vector3d newPos = VectorConversionsMCKt.toJOML(original.getCenter());

        // Transform target thisShip -> world
        if (targetShip != null) {
            newPos = targetShip.getTransform().getShipToWorld().transformPosition(newPos);
        }

        // If we're on a ship, transform target from world -> ourship
        if (thisShip != null) {
            newPos = thisShip.getTransform().getWorldToShip().transformPosition(newPos);
        }

        // Should be original if we didn't need to transform
        return BlockPos.containing(VectorConversionsMCKt.toMinecraft(newPos));
    }
}
