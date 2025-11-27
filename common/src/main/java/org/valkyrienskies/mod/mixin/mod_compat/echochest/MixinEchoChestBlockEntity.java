package org.valkyrienskies.mod.mixin.mod_compat.echochest;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = { "fuzs.echochest.world.level.block.entity.EchoChestBlockEntity" }, remap = false)
public class MixinEchoChestBlockEntity {
    @WrapMethod(method = "isOccluded")
    private static boolean adjustOcclusionForWorldPosition(Level level, Vec3 pos1, Vec3 pos2, Operation<Boolean> original) {
        // This mod reimplements isOccluded from vanilla sculk behavior, so the fix from feature/sculk does not apply here.
        return MixinVibrationSystemAccessor.isOccluded(level, pos1, pos2);
    }
}
