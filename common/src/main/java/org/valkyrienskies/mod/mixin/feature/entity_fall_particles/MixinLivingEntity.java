package org.valkyrienskies.mod.mixin.feature.entity_fall_particles;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor;

/**
 * This class will temporarily translate the entity into ship position if it breaks fall on a block in ship.
 * The position where fall particles spawn isn't entity's feet position or the block's position,
 * but a position that is calculated from both of them.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {
    public MixinLivingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
        method = "checkFallDamage",
        at = @At("HEAD")
    )
    private void toShip(double d, boolean bl, BlockState blockState, BlockPos blockPos, CallbackInfo ci, @Share("originalPos")
        LocalRef<Vec3> originalPos) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level(), blockPos);
        if (ship != null) {
            originalPos.set(this.position());
            final Vector3d posInShip = ship.getTransform().getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(this.position()));
            ((EntityAccessor)this).setPosNoUpdates(VectorConversionsMCKt.toMinecraft(posInShip));
        }
    }

    @Inject(
        method = "checkFallDamage",
        at = @At("RETURN")
    )
    private void toWorld(double d, boolean bl, BlockState blockState, BlockPos blockPos, CallbackInfo ci, @Share("originalPos")
    LocalRef<Vec3> originalPos){
        if(originalPos.get() != null) {
            ((EntityAccessor)this).setPosNoUpdates(originalPos.get());
        }
    }
}
