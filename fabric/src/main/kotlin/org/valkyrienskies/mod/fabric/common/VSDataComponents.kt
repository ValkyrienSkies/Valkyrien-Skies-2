package org.valkyrienskies.mod.fabric.common

import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

object VSDataComponents {
    private fun <T> register(name: String, builder: () -> DataComponentType<T>): DataComponentType<T> {
        val resourceLocation = ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, name)
        return Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            resourceLocation,
            builder(),
        )
    }

    fun registerDataComponents() {
        ValkyrienSkiesMod.BLOCK_POS_COMPONENT = register("coordinate") {
            DataComponentType.builder<BlockPos>().persistent(BlockPos.CODEC).build()
        }
    }
}
