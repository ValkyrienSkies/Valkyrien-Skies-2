package org.valkyrienskies.mod.common.fluid;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

public final class ClientFluidTypes {
    private static volatile Map<Integer, FlowingFluid> fluids = Map.of();

    private ClientFluidTypes() {
    }

    public static void replace(final Map<Integer, String> registryNames) {
        final Map<Integer, FlowingFluid> resolved = new HashMap<>();
        registryNames.forEach((id, name) -> {
            final ResourceLocation location = ResourceLocation.tryParse(name);
            if (location != null && BuiltInRegistries.FLUID.getOptional(location).orElse(null)
                instanceof final FlowingFluid fluid) {
                resolved.put(id, fluid);
            }
        });
        fluids = Map.copyOf(resolved);
    }

    public static @Nullable FlowingFluid get(final int id) {
        return fluids.get(id);
    }

    public static void clear() {
        fluids = Map.of();
    }
}
