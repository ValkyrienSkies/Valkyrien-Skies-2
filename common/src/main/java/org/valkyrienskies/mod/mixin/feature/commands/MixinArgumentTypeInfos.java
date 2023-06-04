package org.valkyrienskies.mod.mixin.feature.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.command.RelativeVector3Argument;
import org.valkyrienskies.mod.common.command.ShipArgument;
import org.valkyrienskies.mod.common.command.ShipArgumentInfo;

@Mixin(ArgumentTypeInfos.class)
public class MixinArgumentTypeInfos {
    @Shadow
    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> ArgumentTypeInfo<A, T> register(
        Registry<ArgumentTypeInfo<?, ?>> arg, String string, Class<? extends A> class_, ArgumentTypeInfo<A, T> arg2) {
        throw new IllegalStateException();
    }

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void postBootstrap(final Registry<ArgumentTypeInfo<?, ?>> registry,
        final CallbackInfoReturnable<ArgumentTypeInfo<?, ?>> ci) {
        register(registry, "valkyrienskies:ship_argument", ShipArgument.class, new ShipArgumentInfo());
        register(registry, "valkyrienskies:relative_vector3_argument", RelativeVector3Argument.class,
            SingletonArgumentInfo.contextFree(RelativeVector3Argument::new));
    }
}
