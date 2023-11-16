package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IExtendedAirCurrentSource;

@Mixin(EncasedFanBlockEntity.class)
public abstract class MixinEncasedFanTileEntity extends KineticBlockEntity implements IExtendedAirCurrentSource {
    @Unique
    private Ship ship = null;

    public MixinEncasedFanTileEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);
        ship = VSGameUtilsKt.getShipManagingPos(level, getBlockPos());
    }

    @Override
    public Ship getShip() {
        return ship;
    }


}
