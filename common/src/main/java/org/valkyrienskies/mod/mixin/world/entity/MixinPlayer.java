package org.valkyrienskies.mod.mixin.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity implements PlayerDuck {

    @Unique
    private final MinecraftPlayer vsPlayer = new MinecraftPlayer(Player.class.cast(this));

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType,
        Level level) {
        super(entityType, level);
    }

    @Override
    public MinecraftPlayer vs_getPlayer() {
        return vsPlayer;
    }

    @Shadow
    public abstract double blockInteractionRange();

    @Inject(
        method = "canInteractWithBlock",
        at = @At("RETURN"),
        cancellable = true
    )
    private void includeShipsInDistanceCheck(BlockPos blockPos, double reachDistance, CallbackInfoReturnable<Boolean> cir) {
        // If the player can already interact then just return
        if (cir.getReturnValueZ()) {
            return;
        }
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level(), blockPos);
        if (ship != null) {
            final double e = this.blockInteractionRange() + reachDistance;
            final Vec3 eyePosInShip = VectorConversionsMCKt.toMinecraft(ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(getEyePosition())));
            // Handle scaling
            final double distanceSq = (new AABB(blockPos)).distanceToSqr(eyePosInShip) * ship.getTransform().getShipToWorldScaling().x() * ship.getTransform().getShipToWorldScaling().x();
            cir.setReturnValue(distanceSq < e * e);
        }
    }
}
