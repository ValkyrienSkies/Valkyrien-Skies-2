package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import li.cil.tis3d.client.renderer.RenderContextImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Position;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.ModifyArg;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(RenderContextImpl.class)
public abstract class MixinRenderContextImpl {
    @ModifyArg(method = "closeEnoughForDetails()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position,F)Z"), index = 0)
    public Boolean ValkrienSkies$closerToCenterThan(Position pos) {
        let ship = VSGameUtilsKt.getShipObjectManagingPos(,pos)
    }
}
