package org.valkyrienskies.mod.forge.mixin.compat.oc2r;

import com.llamalad7.mixinextras.sugar.Local;
import li.cil.oc2.client.renderer.ProjectorDepthRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(ProjectorDepthRenderer.class)
public abstract class MixinProjectorDepthRenderer {
    @ModifyVariable(method = "renderProjectorDepths", at = @At("STORE"), ordinal = 1, remap = false)
    private static Vec3 valkyrienskies$transformProjectorPosToWorld(Vec3 original, @Local(argsOnly = true) ClientLevel level) {
        Ship ship = ValkyrienSkies.getShipManagingBlock(level, original);
        if (ship == null) {
            return original;
        }

        return ValkyrienSkies.positionToWorld(ship, original);
    }
}
