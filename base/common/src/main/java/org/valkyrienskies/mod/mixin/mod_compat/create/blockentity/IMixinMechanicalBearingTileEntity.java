package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MechanicalBearingBlockEntity.class)
public interface IMixinMechanicalBearingTileEntity {
    @Accessor boolean isAssembleNextTick();
}
