package org.valkyrienskies.mod.forge.mixin.compat.mekanism;

import java.util.Objects;
import mekanism.common.lib.radiation.RadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
    @ModifyVariable(remap = false, ordinal = 0, method = "radiate(Lnet/minecraft/core/GlobalPos;D)V", at = @At("HEAD"), argsOnly = true)
    private GlobalPos MixinDumpRadiation(final GlobalPos coord4D) {
        final ResourceKey<Level> resourceKey = coord4D.dimension();
        final Level level = Objects.requireNonNull(ValkyrienSkiesMod.getCurrentServer()).getLevel(resourceKey);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, coord4D.pos());
        if (ship == null){
            return coord4D;
        } else{
            return GlobalPos.of(coord4D.dimension(), BlockPos.containing(VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, coord4D.pos()))));
        }
    }
}
