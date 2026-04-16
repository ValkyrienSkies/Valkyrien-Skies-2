package org.valkyrienskies.mod.mixin.mod_compat.create;

import net.createmod.catnip.levelWrappers.PlacementSimulationServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.mixinducks.world.level.NoVSLevelDuck;

@Mixin(PlacementSimulationServerLevel.class)
public class MixinPlacementSimulationServerLevel implements NoVSLevelDuck {
}
