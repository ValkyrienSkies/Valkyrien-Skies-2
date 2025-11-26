package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.create.DeployerScrollOptionSlot;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IDeployerBehavior;

@Pseudo
@Mixin(DeployerBlockEntity.class)
public abstract class MixinDeployerBlockEntity extends KineticBlockEntity implements IDeployerBehavior {

    @Unique
    protected ScrollOptionBehaviour<WorkingMode> valkyrienskies$workingMode;

    public MixinDeployerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Inject(
            method = "addBehaviours",
            at = @At("RETURN"),
            remap = false
    )
    public void behaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        this.valkyrienskies$workingMode = new ScrollOptionBehaviour<>(
                WorkingMode.class,
                Component.translatable("misc.valkyrienskies.create.deployer_working_mode"),
                (DeployerBlockEntity)(Object) this,
                valkyrienskies$getMovementModeSlot()
        );
        behaviours.add(this.valkyrienskies$workingMode);
    }

    @Unique
    private ValueBoxTransform valkyrienskies$getMovementModeSlot() {
        return new DeployerScrollOptionSlot((state, d) -> {
            Direction.Axis axis = d.getAxis();

            return axis == getSlot(state.getValue(DeployerBlock.FACING).getAxis(),
                    state.getValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE));
        });
    }

    @Override
    public ScrollOptionBehaviour<IDeployerBehavior.WorkingMode> valkyrienskies$get_working_mode() {
        return valkyrienskies$workingMode;
    }

    @Unique
    public Direction.Axis getSlot(Direction.Axis axis, boolean b) {
        switch (axis) {
            case Y -> {
                return b ? Direction.Axis.Z : Direction.Axis.X;
            }
            case X -> {
                return b ? Direction.Axis.Z : Direction.Axis.Y;
            }
            default -> {
                return b ? Direction.Axis.Y : Direction.Axis.X;
            }
        }
    }
}
