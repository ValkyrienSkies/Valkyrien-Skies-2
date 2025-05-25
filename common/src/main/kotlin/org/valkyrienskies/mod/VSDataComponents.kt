package org.valkyrienskies.mod

import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

object VSDataComponents {
    val BLOCK_POS_COMPONENT: DataComponentType<BlockPos> = register("coordinate") {
        DataComponentType.builder<BlockPos>().persistent(BlockPos.CODEC).build()
    }

    private fun <T> register(name: String, builder: () -> DataComponentType<T>): DataComponentType<T> {
        val resourceLocation = ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, name)
        return Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            resourceLocation,
            builder(),
        )
    }

    // Invoking this function loads this object class, which registers the data components
    fun registerDataComponents() {}
}
