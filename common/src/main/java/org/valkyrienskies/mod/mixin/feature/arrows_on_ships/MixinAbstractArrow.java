package org.valkyrienskies.mod.mixin.feature.arrows_on_ships;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.DefaultShipyardEntityHandler;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;

@Mixin(AbstractArrow.class)
public abstract class MixinAbstractArrow extends Projectile {
    @Unique
    @Nullable BlockPos vs$groundPos;

    /**
     * When checking whether the grounded state is still valid, blockstate is read at the position of arrow entity.
     * On ships this fails as the arrow collides with shipyard blocks but checks for blocks in its world coordinates.
     * Grounded arrows are immobile so checking against hit BlockPos and not arrow coordinates should yield the same
     * result for all intents and purposes
     */
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    BlockState trustSavedGroundPos(Level level, BlockPos blockPos, Operation<BlockState> original) {
        return original.call(level, vs$groundPos != null ? vs$groundPos : blockPos);
    }

    @WrapMethod(
        method = "addAdditionalSaveData"
    )
    void saveGroundPos(CompoundTag compoundTag, Operation<Void> original) {
        original.call(compoundTag);
        if (vs$groundPos != null) {
            compoundTag.put("GroundPos", NbtUtils.writeBlockPos(vs$groundPos));
        }
    }

    @WrapMethod(
        method = "readAdditionalSaveData"
    )
    void loadGroundPos(CompoundTag compoundTag, Operation<Void> original) {
        original.call(compoundTag);
        if (compoundTag.contains("GroundPos")) {
            vs$groundPos = NbtUtils.readBlockPos(compoundTag.getCompound("GroundPos"));
        }
    }

    /**
     * If the arrow hits a shipyard block, teleport it to the corresponding coordinates and reassign its entity handler
     * to shipyard. Block hit position is saved for future use
     */
    @Inject(
        method = "onHitBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/Projectile;onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;)V",
            shift = Shift.AFTER
        )
    )
    void storeHitAndArrowPos(
        BlockHitResult hit, CallbackInfo ci
    ) {
        vs$groundPos = hit.getBlockPos();
        Ship ship = VSGameUtilsKt.getShipObjectManagingPos(level(), vs$groundPos);
        if (ship != null) {
            VSEntityManager.INSTANCE.setCustomHandler(this, DefaultShipyardEntityHandler.INSTANCE);
            DefaultShipyardEntityHandler.INSTANCE.moveEntityFromWorldToShipyard(this, ship);
        }
    }

    /**
     * Called when arrow loses its ground. Removing the entity handler override and teleport to world coordinates.
     */
    @Inject(
        method = "startFalling",
        at = @At("HEAD")
    )
    void detachArrowFromShip(CallbackInfo ci) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level(), this.position());
        if (ship != null) {
            VSEntityManager.INSTANCE.setCustomHandler(this, null);
            WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(this, ship);
        }
    }

    // Dummy constructor
    public MixinAbstractArrow(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }
}
