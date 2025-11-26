package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(BlazeBurnerBlockEntity.class)
public abstract class MixinBlazeBurnerBlockEntity extends SmartBlockEntity {

    @WrapOperation(
        method = "tickAnimation",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D")
    )
    private double getPlayerX(LocalPlayer player, Operation<Double> original){
        Ship ship = VSGameUtilsKt.getShipManagingPos(this.level, this.worldPosition);
        if(ship != null) {
            return ship.getTransform().getWorldToShip().transformPosition(player.getX(), player.getY(), player.getZ(), new Vector3d()).x;
        } else return original.call(player);
    }

    @WrapOperation(
        method = "tickAnimation",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D")
    )
    private double getPlayerZ(LocalPlayer player, Operation<Double> original){
        Ship ship = VSGameUtilsKt.getShipManagingPos(this.level, this.worldPosition);
        if(ship != null) {
            return ship.getTransform().getWorldToShip().transformPosition(player.getX(), player.getY(), player.getZ(), new Vector3d()).z;
        } else return original.call(player);
    }

    // Dummy
    private MixinBlazeBurnerBlockEntity(final BlockEntityType<?> type, final BlockPos pos,
        final BlockState state) {
        super(type, pos, state);
    }
}
