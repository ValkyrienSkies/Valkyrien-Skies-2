package org.valkyrienskies.mod.mixin.mod_compat.moonlight;

import net.mehvahdjukaar.moonlight.core.misc.FakeServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.mixinducks.world.level.NoVSLevelDuck;

@Mixin(FakeServerLevel.class)
public class MixinFakeServerLevel implements NoVSLevelDuck {
}
