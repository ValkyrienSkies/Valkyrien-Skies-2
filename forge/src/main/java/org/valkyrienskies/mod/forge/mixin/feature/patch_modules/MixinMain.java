package org.valkyrienskies.mod.forge.mixin.feature.patch_modules;

import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Main.class, net.minecraft.client.main.Main.class})
public class MixinMain {

    // https://github.com/Kotlin/kotlinx.coroutines/issues/2237
    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void fixModules(final String[] args, final CallbackInfo ci) {
        final var module = Main.class.getModule();
        final var layer = module.getLayer();

        layer.findModule("kotlin.stdlib").ifPresent((m1) -> {
            System.out.println("Found kotlin.stdlib");
            layer.findModule("kotlinx.coroutines.core.jvm").ifPresent((m2) -> {
                System.out.println("Found kotlinx.coroutines.core.jvm");

                module.addUses(BiConsumer.class);

                ServiceLoader.load(ModuleLayer.boot(), BiConsumer.class)
                    .stream()
                    .filter((s) -> s.type().getModule().getName().equals("me.ewoudje.lazy_module_patcher"))
                    .map(ServiceLoader.Provider::get)
                    .forEach((c) -> c.accept(m1, m2));
            });
        });
    }

}
