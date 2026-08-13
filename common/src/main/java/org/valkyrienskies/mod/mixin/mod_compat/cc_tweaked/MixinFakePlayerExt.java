package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * Mixin the forge CC:T FakePlayer
 */
@Pseudo
@Mixin(targets = {"dan200.computercraft.shared.platform.FakePlayerExt"}, remap = false)
public abstract class MixinFakePlayerExt implements IEntityDraggingInformationProvider {
    @Override
    public boolean vs$shouldDrag() {
        return false;
    }
}
