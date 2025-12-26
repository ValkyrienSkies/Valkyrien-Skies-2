package org.valkyrienskies.mod.mixin.mod_compat.create.accessors;

import net.createmod.catnip.ghostblock.GhostBlockParams;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GhostBlockParams.class)
public interface GhostBlockParamsAccessor {
    @Accessor("pos")
    BlockPos getPos();

    @Accessor("state")
    BlockState getState();
}
