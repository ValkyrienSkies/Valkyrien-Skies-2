package org.valkyrienskies.mod.mixin.feature.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentSerializer;
import net.minecraft.commands.synchronization.ArgumentTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.command.VSCommands;

@Mixin(ArgumentTypes.class)
public class MixinArgumentTypes {

    @Shadow
    public static <T extends ArgumentType<?>> void register(final String id, final Class<T> argClass, final ArgumentSerializer<T> serializer) {
        throw new AssertionError();
    }

    @Inject(method = "bootStrap()V", at = @At("RETURN"))
    private static void register(final CallbackInfo ci) {
        VSCommands.INSTANCE.bootstrap();
    }

}
