package org.valkyrienskies.mod.mixin.mod_compat.create.behaviour;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BlockBreakingMovementBehaviour.class)
public class MixinBlockBreakingMovementBehaviour {
    //Region start - fix equals -0 != 0
    private Vec3 flatten(Vec3 vec3) {
        if (vec3.x == -0) {
            vec3 = new Vec3(0, vec3.y, vec3.z);
        }
        if (vec3.y == -0) {
            vec3 = new Vec3(vec3.x, 0, vec3.z);
        }
        if (vec3.z == -0) {
            vec3 = new Vec3(vec3.x, vec3.y, 0);
        }
        return vec3;
    }

    @Redirect(method = "tickBreaker", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;equals(Ljava/lang/Object;)Z"))
    private boolean redirectEquals(Vec3 instance, final Object vec3) {
        Vec3 other = (Vec3) vec3;
        other = flatten(other);
        instance = flatten(instance);
        return instance.equals(other);
    }
    //Region end
    //Region start - fix entity throwing not being aligned to ship
    @Unique
    private MovementContext movementContext;

    @Inject(method = "throwEntity", at = @At("HEAD"), remap = false)
    private void injectThrowEntity(final MovementContext context, final Entity entity, final CallbackInfo ci) {
        movementContext = context;
    }

    @Redirect(method = "throwEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), remap = false)
    private void redirectSetDeltaMovement(final Entity instance, Vec3 motion) {
        if (movementContext != null && VSGameUtilsKt.isBlockInShipyard(movementContext.world, movementContext.contraption.anchor)) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(movementContext.world, movementContext.contraption.anchor);
            if (ship != null)
                motion = VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformDirection(VectorConversionsMCKt.toJOML(motion), new Vector3d()));
        }
        instance.setDeltaMovement(motion);
    }
    //Region end
}
