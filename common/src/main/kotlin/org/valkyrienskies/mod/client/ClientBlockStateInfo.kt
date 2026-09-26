package org.valkyrienskies.mod.client

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import org.jetbrains.annotations.ApiStatus
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.getOrOther
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.stateToString
import org.valkyrienskies.mod.common.blockstate.BlockStateProperties
import org.valkyrienskies.mod.common.blockstate.LiquidStateProperties
import org.valkyrienskies.mod.common.blockstate.SolidStateProperties

/**
 * For getting block mass, friction, and elasticity on the client side
 */
object ClientBlockStateInfo {
    private val blockState2Properties: MutableMap<ResourceLocation, MutableMap<String, BlockStateProperties>> = HashMap()

    /**
     * True if mass gets synced to client.
     * Only serves to disable mass tooltip and jei search when mass doesn't get synced to prevent confusion
     */
    var clientHasMassInfo = false

    /**
     * Used to register clientside block data. For internal use only.
     */
    @ApiStatus.Internal
    fun registerBlockInfo(id: ResourceLocation, values: String, properties: BlockStateProperties) {
        blockState2Properties.computeIfAbsent(id) { mutableMapOf() }[values] = properties
    }

    fun getProperties(blockState: BlockState): BlockStateProperties? {
        val string = stateToString(blockState)
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    fun getSolidProperties(blockState: BlockState): SolidStateProperties? {
        val properties = getProperties(blockState)
        return properties?.solid
    }

    fun getProperties(fluidState: FluidState): BlockStateProperties? {
        val string = stateToString(BlockStateInfoResolver.serializeFluid(fluidState))
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    fun getLiquidProperties(fluidState: FluidState): LiquidStateProperties? {
        val properties = getProperties(fluidState)
        return properties?.liquid
    }

    fun getProperties(id: ResourceLocation): BlockStateProperties? {
        return blockState2Properties[id]?.get("default")
    }

    fun setDefaultValues(mass: Double?, friction: Double?, elasticity: Double?, density: Double?) {
        defaultMass = mass
        defaultFriction = friction
        defaultElasticity = elasticity
        defaultDensity = density
    }

    var defaultMass: Double? = null
    var defaultFriction: Double? = null
    var defaultElasticity: Double? = null
    var defaultDensity: Double? = null
    /**
     * Clears data and disables mass tooltip
     */
    @ApiStatus.Internal
    fun disable() {
        clientHasMassInfo = false
        blockState2Properties.clear()
        setDefaultValues(null, null, null, null)
    }
}
