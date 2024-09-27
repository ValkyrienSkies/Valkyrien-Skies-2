package org.valkyrienskies.mod.mixin.mod_compat.bluemap;

import de.bluecolored.bluemap.core.map.hires.TileModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TileModel.class)
@Pseudo
public interface HiresTileModelAccessor {

    @Accessor("position")
    float[] getPositions();

}
