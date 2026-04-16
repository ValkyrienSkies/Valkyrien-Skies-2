package org.valkyrienskies.mod.mixin.mod_compat.echochest;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem.Listener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Listener.class)
public interface MixinVibrationSystemAccessor {
    @Invoker("isOccluded")
    static boolean isOccluded(Level level, Vec3 pos1, Vec3 pos2) {
        return false;
    }
}
