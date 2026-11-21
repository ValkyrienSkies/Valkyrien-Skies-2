package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.shaders.Program;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.client.render.ShipMaterials;

@Mixin(ShaderInstance.class)
public abstract class MixinShipMaterialShader {
    @WrapMethod(method = "getOrCreate")
    private static Program vs$loadShipMaterials(final ResourceProvider resources, final Program.Type type,
        final String name, final Operation<Program> original) {
        final boolean shipFragment = type == Program.Type.FRAGMENT &&
            (name.startsWith("rendertype_ship_") || name.startsWith("minecraft:rendertype_ship_"));
        return original.call(shipFragment ? ShipMaterials.shaderResources(resources) : resources, type, name);
    }
}
