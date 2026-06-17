package org.valkyrienskies.mod.mixin.mod_compat.sodium;


import java.util.List;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.SodiumOptionsMenu;

@Mixin(SodiumOptionsGUI.class)
public class MixinSodiumOptionsGUI extends Screen {
    @Shadow(remap = false)
    @Final
    private List<Object> pages;

    protected MixinSodiumOptionsGUI(Component component) {
        super(component);
    }

    @Dynamic
    @Inject(method = "<init>", at = @At("RETURN"))
    private void lambdynlights$onInit(Screen prevScreen, CallbackInfo ci) {
        this.pages.add(SodiumOptionsMenu.INSTANCE.makeSodiumOptionPage());
    }

}
