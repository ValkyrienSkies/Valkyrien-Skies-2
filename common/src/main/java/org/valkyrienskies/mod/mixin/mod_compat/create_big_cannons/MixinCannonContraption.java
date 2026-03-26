package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;

@Mixin(targets = {
    "rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption",
    "rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption",
    })
public class MixinCannonContraption {
    @WrapOperation(
        method = "fireShot",
        at = @At(
            value = "INVOKE",
            target = "Lrbasamoyai/createbigcannons/cannon_control/ControlPitchContraption;onRecoil(Lnet/minecraft/world/phys/Vec3;Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;)V"
        )
    )
    public void onFireShot(ControlPitchContraption instance, Vec3 vector, AbstractContraptionEntity cannon,
        Operation<Void> original, @Local(name = "recoilMagnitude") float recoilMagnitude) {
        vs$handleRecoil(instance, vector, cannon, recoilMagnitude);
        original.call(instance, vector, cannon);
    }

    @Unique
    private void vs$handleRecoil(ControlPitchContraption instance, Vec3 vector, AbstractContraptionEntity cannon, float magnitude) {
        LoadedServerShip ship = (LoadedServerShip) VSGameUtilsKt.getLoadedShipManagingPos(cannon.level(), BlockPos.containing(cannon.getAnchorVec()));
        // We aren't on a ship, nothing to apply recoil to
        if (ship == null) return;
        if (!VSGameConfig.SERVER.getCbc().getShellRecoil()) return;

        GameToPhysicsAdapter applier = ValkyrienSkiesMod.getOrCreateGTPA(VSGameUtilsKt.getDimensionId(cannon.level()));
        // Invert (by mult by -1) because magnitude is in direction the cannon shot, not the direction recoil is
        double recoilForce = magnitude * VSGameConfig.SERVER.getCbc().getShellRecoilMult() * -1;
        applier.applyWorldForceToBodyPos(
            ship.getId(),
            ship.getTransform().getShipToWorldRotation().transform(VectorConversionsMCKt.toJOML(vector).negate().normalize()).mul(recoilForce),
            VectorConversionsMCKt.toJOML(cannon.getAnchorVec().add(0.5, 0.5, 0.5)).sub(ship.getTransform().getPositionInShip())
        );

    }
}
