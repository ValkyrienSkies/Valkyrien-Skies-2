package org.valkyrienskies.mod.mixin.mod_compat.create.client;

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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class MixinChainConveyorBlockEntity extends KineticBlockEntity implements TransformableBlockEntity {
    @Unique
    private Vector3d vs$shipPrevVelocity;

    private MixinChainConveyorBlockEntity(BlockEntityType<?> typeIn,
        BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @WrapOperation(
        method = "tickBoxVisuals(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;)V",
        at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage$ChainConveyorPackagePhysicsData;motion:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.PUTFIELD)
    )
    private void adjustToShipAcceleration(ChainConveyorPackagePhysicsData instance, Vec3 value,
        Operation<Void> original){
        ClientShip ship = VSClientGameUtils.getClientShip(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        if (ship != null) {
            if (vs$shipPrevVelocity != null) {
                Vector3d acceleration = ship.getVelocity().sub(vs$shipPrevVelocity, new Vector3d());
                acceleration = ship.getWorldToShip().transformDirection(acceleration);
                original.call(instance, value.add(VectorConversionsMCKt.toMinecraft(acceleration).scale(0.25)));
            }
            vs$shipPrevVelocity = new Vector3d(ship.getVelocity());
            return;
        }
        original.call(instance, value);
    }
}
