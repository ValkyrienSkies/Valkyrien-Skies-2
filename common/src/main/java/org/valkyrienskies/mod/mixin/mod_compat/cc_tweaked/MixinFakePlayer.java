package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * Mixin the Fabric CC:T FakePlayer
 */
@Pseudo
@Mixin(targets = {"dan200.computercraft.shared.platform.FakePlayer"}, remap = false)
public abstract class MixinFakePlayer implements IEntityDraggingInformationProvider {
    @Override
    public boolean vs$shouldDrag() {
        return false;
    }
}
