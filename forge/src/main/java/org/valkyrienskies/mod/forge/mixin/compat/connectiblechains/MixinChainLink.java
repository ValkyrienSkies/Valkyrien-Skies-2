package org.valkyrienskies.mod.forge.mixin.compat.connectiblechains;

import com.lilypuree.connectiblechains.chain.ChainLink;
import com.lilypuree.connectiblechains.entity.ChainKnotEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = ChainLink.class, remap = false)
public abstract class MixinChainLink {
    @Final
    @Shadow
    public ChainKnotEntity primary;
    @Final
    @Shadow
    public Entity secondary;

    @Inject(method = "createCollision", at = @At("HEAD"), cancellable = true)
    private void skipCreatingCollision(CallbackInfo ci) {
        if (VSGameUtilsKt.getShipManaging(primary) != VSGameUtilsKt.getShipManaging(secondary)) ci.cancel();
    }

    @WrapOperation(method = "destroy", at = @At(value = "INVOKE", target = "Lcom/lilypuree/connectiblechains/util/Helper;middleOf(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 middleOfWorldPositions(Vec3 a, Vec3 b, Operation<Vec3> original, @Local Level level) {
        return original.call(VSGameUtilsKt.toWorldCoordinates(level, a), VSGameUtilsKt.toWorldCoordinates(level, b));
    }
}
