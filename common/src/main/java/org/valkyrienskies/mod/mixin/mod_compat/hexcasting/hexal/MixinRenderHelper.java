package org.valkyrienskies.mod.mixin.mod_compat.hexcasting.hexal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import ram.talia.hexal.api.linkable.ILinkable.IRenderCentre;
import ram.talia.hexal.client.RenderHelperKt;

@Pseudo
@Mixin(RenderHelperKt.class)
public class MixinRenderHelper {
    @WrapOperation(method = "playLinkParticles", at = @At(value = "INVOKE",
        target = "Lram/talia/hexal/api/linkable/ILinkable$IRenderCentre$DefaultImpls;renderCentre$default(Lram/talia/hexal/api/linkable/ILinkable$IRenderCentre;Lram/talia/hexal/api/linkable/ILinkable$IRenderCentre;ZILjava/lang/Object;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 valkyrienskies$transformSource(IRenderCentre source, IRenderCentre sink, boolean b, int i, Object o, Operation<Vec3> original, @Local(argsOnly = true) Level level) {
        Vec3 sourceCenter = original.call(source, sink, b, i, o);
        if (ValkyrienSkies.getShipManagingBlock(level, sourceCenter) instanceof ClientShip ship)
            sourceCenter = ValkyrienSkies.positionToWorld(ship, sourceCenter);
        return sourceCenter;
    }
}
