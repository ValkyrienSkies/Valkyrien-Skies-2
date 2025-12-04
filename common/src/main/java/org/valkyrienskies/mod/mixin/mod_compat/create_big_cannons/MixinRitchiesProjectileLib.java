package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.ritchiesprojectilelib.RitchiesProjectileLib;

@Mixin(RitchiesProjectileLib.class)
public class MixinRitchiesProjectileLib {
    @WrapMethod(
        method = "queueForceLoad"
    )
    private static void cancelIfShipyard(ServerLevel level, int chunkX, int chunkZ, Operation<Void> original){
        if(VSGameUtilsKt.getShipManagingPos(level, chunkX, chunkZ) != null) return;
        original.call(level, chunkX, chunkZ);
    }
}
