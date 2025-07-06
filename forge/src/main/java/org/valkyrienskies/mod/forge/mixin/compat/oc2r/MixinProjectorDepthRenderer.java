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
public class MixinProjectorDepthRenderer {
    @ModifyVariable(method = "renderProjectorDepths", at = @At(
        value = "STORE",
        target = "Lli/cil/oc2/client/renderer/ProjectorDepthRenderer;renderProjectorDepths(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/ClientLevel;FI)V"
    ), remap = false)
    private static Vec3 valkyrienskies$renderProjectorDepthsOnShips(Vec3 instance, @Local(argsOnly = true) ClientLevel level) {
        Ship ship = ValkyrienSkies.getShipManagingBlock(level, instance);
        if (ship == null) return instance;
        
        return ValkyrienSkies.positionToWorld(ship, instance);
    }
}
