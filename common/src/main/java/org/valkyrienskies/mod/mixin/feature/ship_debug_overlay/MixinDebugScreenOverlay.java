package org.valkyrienskies.mod.mixin.feature.ship_debug_overlay;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private HitResult block;
    @Shadow
    protected abstract Level getLevel();

    @Inject(method = "getGameInformation", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1))
    private void addShipCountInformation(CallbackInfoReturnable<List<String>> cir, @Local List<String> list) {
        Level l = getLevel();
        if (l instanceof ServerLevel) {
            ServerShipWorld world = VSGameUtilsKt.getShipObjectWorld((ServerLevel)l);
            list.add("Ships Loaded: " + world.getLoadedShips().size() + "/" + world.getAllShips().size());
        }
    }

    @Inject(method = "getGameInformation", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 6, shift = At.Shift.AFTER))
    private void addPlayerDraggingInformation(CallbackInfoReturnable<List<String>> cir, @Local List<String> list) {
        EntityDraggingInformation info = ((IEntityDraggingInformationProvider)minecraft.player).getDraggingInformation();
        if (info != null) {
            if (info.isEntityBeingDraggedByAShip()) {
                Long shipId = info.getLastShipStoodOn();
                if (shipId != null) {
                    Ship ship = VSGameUtilsKt.getAllShips(getLevel()).getById(shipId);
                    if (ship != null) {
                        list.add("Dragged by: " + VSGameUtilsKt.getAllShips(getLevel()).getById(info.getLastShipStoodOn()).getSlug());
                    }
                }
            }
        }
    }

    @Inject(method = "getSystemInformation", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
    private void addShipInformation(CallbackInfoReturnable<List<String>> cir, @Local List<String> list) {
        Level l = getLevel();
        BlockPos blockPos = ((BlockHitResult) this.block).getBlockPos();
        Ship ship = VSGameUtilsKt.getShipManagingPos(l, blockPos);
        LoadedServerShip lsship = l instanceof ServerLevel ? VSGameUtilsKt.getLoadedShipManagingPos((ServerLevel) l, blockPos) : null;
        if (ship != null) {
            list.add("");
            list.add(ChatFormatting.UNDERLINE + "Targeted Ship: " + ship.getSlug());
            if (lsship != null) {
                // Info about physical properties is only visible in singleplayer or while hosting an integrated server.
                list.add("Static: " + lsship.isStatic());
                list.add("Mass: " + lsship.getInertiaData().getMass() + " kg");
                list.add("");
            }
            Vector3dc scale = ship.getTransform().getShipToWorldScaling();
            list.add(String.format(
                        Locale.ROOT,
                        "Ship Scale: %.3f / %.3f / %.3f",
                        scale.x(),
                        scale.y(),
                        scale.z()
            ));
            Vector3dc linVel = ship.getVelocity();
            list.add(String.format(
                    Locale.ROOT,
                    "Linear Velocity: %.3f / %.3f / %.3f, total: %.3f m/s",
                    linVel.x(),
                    linVel.y(),
                    linVel.z(),
                    linVel.length()
            ));
            Vector3dc angVel = ship.getOmega();
            list.add(String.format(
                    Locale.ROOT,
                    "Angular Velocity: %.3f / %.3f / %.3f",
                    angVel.x(),
                    angVel.y(),
                    angVel.z()
            ));
            Vec3 pos = VSGameUtilsKt.toWorldCoordinates(ship, blockPos.getCenter());
            list.add(String.format(
                    Locale.ROOT,
                    "Targeted World Position: %.3f / %.3f / %.3f",
                    pos.x(),
                    pos.y(),
                    pos.z()
            ));
        }
    }
}
