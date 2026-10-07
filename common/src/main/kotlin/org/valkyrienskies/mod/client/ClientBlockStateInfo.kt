package org.valkyrienskies.mod.client

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import org.jetbrains.annotations.ApiStatus
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.getOrOther
import org.valkyrienskies.mod.common.blockstate.SolidProperties
import org.valkyrienskies.mod.common.blockstate.VSBlockProperties
import org.valkyrienskies.mod.common.blockstate.VSFluidProperties
import org.valkyrienskies.mod.common.blockstate.idAndProperties

/**
 * For getting block mass, friction, and elasticity on the client side
 */
object ClientBlockStateInfo {
    private val blockState2Properties: MutableMap<ResourceLocation, MutableMap<String, VSBlockProperties>> = HashMap()
    private val fluidState2Properties: MutableMap<ResourceLocation, MutableMap<String, VSFluidProperties>> = HashMap()

    /**
     * True if mass gets synced to client.
     * Only serves to disable mass tooltip and jei search when mass doesn't get synced to prevent confusion
     */
    var clientHasMassInfo = false

    /**
     * Used to register clientside block data. For internal use only.
     */
    @ApiStatus.Internal
    fun registerBlockInfo(id: ResourceLocation, values: String, properties: VSBlockProperties) {
        blockState2Properties.computeIfAbsent(id) { mutableMapOf() }[values] = properties
    }
    @ApiStatus.Internal
    fun registerBlockInfo(id: ResourceLocation, values: String, properties: VSFluidProperties) {
        fluidState2Properties.computeIfAbsent(id) { mutableMapOf() }[values] = properties
    }

    fun getProperties(blockState: BlockState): VSBlockProperties? {
        val string = blockState.idAndProperties()
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    fun getSolidProperties(blockState: BlockState): SolidProperties? {
        val props = getProperties(blockState)
        return props?.solid
    }

    fun getProperties(fluidState: FluidState): VSFluidProperties? {
        val string = fluidState.idAndProperties()
        return fluidState2Properties[string.a]?.getOrOther(string.b, "default")
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
