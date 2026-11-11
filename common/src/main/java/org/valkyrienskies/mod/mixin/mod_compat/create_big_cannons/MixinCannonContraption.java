package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;

@Pseudo
@Mixin(targets = {
    "rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption",
    "rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption",
    "riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMediumcannonContraption",
    "riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedRotarycannonContraption",
    "riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMunitionsLauncherContraption"
}, remap = false)
public class MixinCannonContraption {
    @WrapOperation(
        method = "fireShot",
        at = @At(
            value = "INVOKE",
            target = "Lrbasamoyai/createbigcannons/cannon_control/ControlPitchContraption;onRecoil"
        )
    )
    private void vs$onFireShot(ControlPitchContraption controller, Vec3 recoil, AbstractContraptionEntity cannon,
        Operation<Void> original) {
        vs$applyShipRecoil(recoil, cannon);
        original.call(controller, recoil, cannon);
    }

    @Unique
    private void vs$applyShipRecoil(Vec3 recoil, AbstractContraptionEntity cannon) {
        if (!(cannon.level() instanceof ServerLevel level) || !VSGameConfig.SERVER.getCbc().getShellRecoil()) return;

        Vec3 anchor = cannon.getAnchorVec();
        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, BlockPos.containing(anchor));
        if (ship == null) return;

        GameToPhysicsAdapter applier = ValkyrienSkiesMod.getOrCreateGTPA(VSGameUtilsKt.getDimensionId(level));
        applier.applyWorldForceToBodyPos(
            ship.getId(),
            ship.getTransform().getShipToWorldRotation().transform(VectorConversionsMCKt.toJOML(recoil))
                .mul(VSGameConfig.SERVER.getCbc().getShellRecoilMult()),
            VectorConversionsMCKt.toJOML(anchor.add(0.5, 0.5, 0.5)).sub(ship.getTransform().getPositionInShip())
        );
    }
}
