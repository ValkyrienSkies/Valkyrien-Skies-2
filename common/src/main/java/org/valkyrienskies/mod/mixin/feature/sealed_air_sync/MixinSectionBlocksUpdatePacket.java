package org.valkyrienskies.mod.mixin.feature.sealed_air_sync;

import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public class MixinSectionBlocksUpdatePacket {
    @Unique
    private boolean[] sealed;
}
