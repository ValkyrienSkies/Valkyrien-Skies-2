package org.valkyrienskies.mod.fabric.integrations.cc_restitched;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.valkyrienskies.core.game.ships.ShipData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShipPeripheral implements IPeripheral {
    private Level level;
    private BlockPos pos;

    public ShipPeripheral(final Level level, final BlockPos blockPos) {
        this.level = level;
        this.pos = blockPos;
    }

    @NotNull
    @Override
    public String getType() {
        return "ship";
    }

    @Override
    public boolean equals(@Nullable final IPeripheral iPeripheral) {
        return VSGameUtilsKt.getShipManagingPos(level, pos) != null;
    }

    @LuaFunction
    public final String getShipName() throws LuaException {
        if (level.isClientSide()) {
            return "";
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            return ship.getName();
        } else {
            throw new LuaException("Not on a Ship");
        }
    }

    @LuaFunction
    public final boolean setShipName(final String string) throws LuaException {
        if (level.isClientSide()) {
            return false;
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            ship.setName(string);
            return true;
        } else {
            throw new LuaException("Not on a Ship");
        }
    }

    @LuaFunction
    public final long getShipID() throws LuaException {
        if (level.isClientSide()) {
            return 0;
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            return ship.getId();
        } else {
            throw new LuaException("Not on a Ship");
        }
    }

    @LuaFunction
    public final double getMass() throws LuaException {
        if (level.isClientSide()) {
            return 0.0;
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            return ship.getInertiaData().getShipMass();
        } else {
            throw new LuaException("Not on a Ship");
        }
    }

    @LuaFunction
    public final Object[] getVelocity() throws LuaException {
        if (level.isClientSide()) {
            return new Object[0];
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            final Vector3dc vel = ship.getVelocity();
            return new Object[] {vel.x(), vel.y(), vel.z()};
        } else {
            throw new LuaException("Not on a Ship");
        }
    }

    @LuaFunction
    public final Object[] getPosition() throws LuaException {
        if (level.isClientSide()) {
            return new Object[0];
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            final Vector3dc vec = ship.getShipTransform().getShipPositionInWorldCoordinates();
            return new Object[] {vec.x(), vec.y(), vec.z()};
        } else {
            throw new LuaException("Not on a Ship");
        }
    }

    @LuaFunction
    public final Object[] getScale() throws LuaException {
        if (level.isClientSide()) {
            return new Object[0];
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            final Vector3dc scale = ship.getShipTransform().getShipCoordinatesToWorldCoordinatesScaling();
            return new Object[] {scale.x(), scale.y(), scale.z()};
        } else {
            throw new LuaException("Not on a Ship");
        }
    }

    @LuaFunction
    public final Object[] getRotation() throws LuaException {
        if (level.isClientSide()) {
            return new Object[0];
        }

        final ShipData ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) level, pos);
        if (ship != null) {
            final Quaterniondc rot = ship.getShipTransform().getShipCoordinatesToWorldCoordinatesRotation();
            return new Object[] {rot.x(), rot.y(), rot.z(), rot.w()};
        } else {
            throw new LuaException("Not on a Ship");
        }
    }
}
