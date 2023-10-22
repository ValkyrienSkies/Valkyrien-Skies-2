package org.valkyrienskies.mod.mixin.accessors.resource;

import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ResourceKey.class)
public interface ResourceKeyAccessor {

    @Accessor("VALUES")
    static Map<String, ResourceKey<?>> getValues() {
        throw new AssertionError();
    }

    @Accessor
    ResourceLocation getRegistryName();

    @Invoker
    static <T> ResourceKey<T> callCreate(final ResourceLocation parent, final ResourceLocation location) {
        throw new AssertionError();
    }
}
