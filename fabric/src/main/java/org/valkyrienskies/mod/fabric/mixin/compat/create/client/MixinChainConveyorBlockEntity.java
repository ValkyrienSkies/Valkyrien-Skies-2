package org.valkyrienskies.mod.fabric.mixin.compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage.ChainConveyorPackagePhysicsData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = ChainConveyorBlockEntity.class)
public abstract class MixinChainConveyorBlockEntity extends KineticBlockEntity implements TransformableBlockEntity {
    @Unique
    private ClientShip vs$ship;

    @Unique
    private Vector3dc vs$shipPrevVelocity;

    @Unique
    private Vector3dc vs$shipCurrentVelocity;

    @Unique
    private Vector3dc vs$shipPrevOmega;

    @Unique
    private Vector3dc vs$shipCurrentOmega;

    @Unique
    private ShipTransform vs$shipPrevTransform;

    private MixinChainConveyorBlockEntity(BlockEntityType<?> typeIn,
        BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Inject(
        method = "tickBoxVisuals()V",
        at = @At("HEAD"),
        remap = false
    )
    private void getShipVelocity(CallbackInfo ci) {
        //This part will check if the Chain Conveyor is on a ship, and update current velocity and omega(angular velocity)
        vs$ship = VSClientGameUtils.getClientShip(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        if (vs$ship != null) {
            vs$shipCurrentVelocity = vs$ship.getVelocity();
            vs$shipCurrentOmega = new Vector3d(vs$ship.getOmega());
        }
        else {
            vs$shipCurrentVelocity = null;
            vs$shipCurrentOmega = null;
        }
    }

    /*
        When the ship velocity/angular velocity changes, the packages dangling in the ship's conveyors will shake.
        This is pure visual effect and doesn't otherwise affect the gameplay.
     */
    @WrapOperation(
        method = "tickBoxVisuals(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;)V",
        at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage$ChainConveyorPackagePhysicsData;motion:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.PUTFIELD, remap = true),
        remap = false
    )
    private void adjustToShipAcceleration(final ChainConveyorPackagePhysicsData instance, final Vec3 value,
        final Operation<Void> original){
        if (vs$ship != null && vs$shipPrevVelocity != null && vs$shipCurrentVelocity != null) {
            //Calculate velocity from linear velocity and angular velocity of the ship.
            final Vector3dc radius = vs$ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(instance.pos)).sub(vs$ship.getTransform().getPositionInWorld());
            final Vector3dc prevRadius = vs$shipPrevTransform.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(instance.prevPos)).sub(vs$shipPrevTransform.getPositionInWorld());

            //Calculate acceleration by subtracting previous point velocity from current point velocity.
            final Vector3d acceleration = new Vector3d(vs$shipCurrentVelocity).add(vs$shipCurrentOmega.cross(radius, new Vector3d()));
            acceleration.sub(new Vector3d(vs$shipPrevVelocity).add(vs$shipPrevOmega.cross(prevRadius, new Vector3d())));
            vs$ship.getWorldToShip().transformDirection(acceleration);

            //Too big acceleration(e.g teleport command) might be better to be clamped, to prevent jitter.
            if (acceleration.length() > 3) acceleration.normalize().mul(3);

            //This was the ship acceleration. By the Galilean relativity we should invert it for boxes' local acceleration.
            original.call(instance, value.add(VectorConversionsMCKt.toMinecraft(acceleration).scale(-0.2)));
        } else original.call(instance, value);
    }

    @Inject(
        method = "tickBoxVisuals()V",
        at = @At("TAIL"),
        remap = false
    )
    private void updatePrevVelocity(CallbackInfo ci){
        //After all the boxes have their acceleration applied, update previous velocity, omega and transform.
        vs$shipPrevVelocity = vs$shipCurrentVelocity;
        vs$shipPrevOmega = vs$shipCurrentOmega;
        if(vs$ship != null) vs$shipPrevTransform = vs$ship.getTransform();
    }
}
