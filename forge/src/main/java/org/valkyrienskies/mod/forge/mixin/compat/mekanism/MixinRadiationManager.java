package org.valkyrienskies.mod.forge.mixin.compat.mekanism;

import mekanism.api.Coord4D;
import mekanism.common.lib.radiation.RadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(RadiationManager.class)
public class MixinRadiationManager  {
    @ModifyVariable(remap = false, ordinal = 0, method = "radiate(Lmekanism/api/Coord4D;D)V", at = @At("HEAD"), argsOnly = true)
    private Coord4D MixinDumpRadiation(final Coord4D coord4D) {
        final ResourceKey<Level> resourceKey = coord4D.dimension;
        final Level level = ValkyrienSkiesMod.getCurrentServer().getLevel(resourceKey);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, coord4D.getPos());
        if (ship == null){
            return coord4D;
        }else{
            return new Coord4D(new BlockPos(
                VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, coord4D.getPos()))), coord4D.dimension);
        }
    }
}
