package org.valkyrienskies.mod.forge.integrations.cc_tweaked;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.shared.computer.blocks.BlockComputer;
import dan200.computercraft.shared.computer.blocks.BlockComputerBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShipPeripheralProvider implements IPeripheralProvider {
    @Override
    public LazyOptional<IPeripheral> getPeripheral(@NotNull final Level level, @NotNull final BlockPos blockPos,
        @NotNull final Direction direction) {
        final BlockState computer = level.getBlockState(blockPos.relative(direction));
        if (!(computer.getBlock() instanceof BlockComputerBase)) {
            return null;
        }
        if (VSGameUtilsKt.getShipManagingPos(level, blockPos) == null) {
            return null;
        }
        if (!(computer.getValue(BlockComputer.FACING).equals(direction.getOpposite()))) {
            return null;
        }

        return LazyOptional.of(() -> new ShipPeripheral(level, blockPos));
    }
}
