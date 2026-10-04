package org.valkyrienskies.mod.client

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import org.jetbrains.annotations.ApiStatus
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.getOrOther
import org.valkyrienskies.mod.common.blockstate.LiquidStateProperties
import org.valkyrienskies.mod.common.blockstate.SolidStateProperties
import org.valkyrienskies.mod.common.blockstate.StateProperties
import org.valkyrienskies.mod.common.blockstate.idAndProperties

/**
 * For getting block mass, friction, and elasticity on the client side
 */
object ClientBlockStateInfo {
    private val blockState2Properties: MutableMap<ResourceLocation, MutableMap<String, StateProperties>> = HashMap()

    /**
     * True if mass gets synced to client.
     * Only serves to disable mass tooltip and jei search when mass doesn't get synced to prevent confusion
     */
    var clientHasMassInfo = false

    /**
     * Used to register clientside block data. For internal use only.
     */
    @ApiStatus.Internal
    fun registerBlockInfo(id: ResourceLocation, values: String, properties: StateProperties) {
        blockState2Properties.computeIfAbsent(id) { mutableMapOf() }[values] = properties
    }

    fun getProperties(blockState: BlockState): StateProperties? {
        val string = blockState.idAndProperties()
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    fun getSolidProperties(blockState: BlockState): SolidStateProperties? {
        val properties = getProperties(blockState)
        return properties?.solid
    }

    fun getProperties(fluidState: FluidState): StateProperties? {
        val string = fluidState.idAndProperties()
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    fun getLiquidProperties(fluidState: FluidState): LiquidStateProperties? {
        val properties = getProperties(fluidState)
        return properties?.liquid
    }

    fun getProperties(id: ResourceLocation): StateProperties? {
        return blockState2Properties[id]?.get("default")
    }
    /**
     * Clears data and disables mass tooltip
     */
    @ApiStatus.Internal
    fun disable() {
        clientHasMassInfo = false
        blockState2Properties.clear()
    }
}
