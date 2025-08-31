package org.valkyrienskies.mod.mixin.mod_compat.common_create.blockentity;

import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

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
        final BlockEntity thisBE = BlockEntity.class.cast(this);
        final BlockPos original = instance.getAnchor();
        final Ship thisShip = VSGameUtilsKt.getShipObjectManagingPos(thisBE.getLevel(), thisBE.getBlockPos());

        return BlockPos.containing(
            CompatUtil.INSTANCE.toSameSpaceAs(
                thisBE.getLevel(),
                original.getCenter(),
                thisShip
            )
        );
    }
}
