package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.shared.turtle.apis.TurtleAPI;
import dan200.computercraft.shared.turtle.core.InteractDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.forge.compat.TurtleReenterCommand;

@Pseudo
@Mixin(TurtleAPI.class)
public abstract class MixinTurtleAPI {
    @Shadow
    protected abstract MethodResult trackCommand(ITurtleCommand command);

    @LuaFunction
    public final MethodResult reenter() {
        return trackCommand(new TurtleReenterCommand(InteractDirection.FORWARD));
    }

    @LuaFunction
    public final MethodResult reenterUp() {
        return trackCommand(new TurtleReenterCommand(InteractDirection.UP));
    }

    @LuaFunction
    public final MethodResult reenterDown() {
        return trackCommand(new TurtleReenterCommand(InteractDirection.DOWN));
    }
}
