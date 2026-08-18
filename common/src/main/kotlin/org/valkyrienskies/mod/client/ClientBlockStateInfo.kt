package org.valkyrienskies.mod.client

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import org.jetbrains.annotations.ApiStatus

data class ClientBlockInfo(
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
)

/**
 * For getting block mass, friction, and elasticity on the client side
 */
object ClientBlockStateInfo {
    private val idToBlockData: MutableMap<ResourceLocation, ClientBlockInfo> = HashMap()

    /**
     * Set by server and used on client side, disables mass tooltip completely if false
     */
    @ApiStatus.Internal
    var shouldAddMassTooltip = false

    /**
     * Used to register clientside block data. For internal use only.
     */
    @ApiStatus.Internal
    fun registerBlockInfo(id: ResourceLocation, clientBlockInfo: ClientBlockInfo) {
        idToBlockData[id] = clientBlockInfo
    }

    fun getBlockInfo(blockState: BlockState): ClientBlockInfo? = idToBlockData[BuiltInRegistries.BLOCK.getKey(blockState.block)]

    fun getBlockInfo(id: ResourceLocation): ClientBlockInfo? = idToBlockData[id]

    /**
     * Clears data and disables mass tooltip
     */
    @ApiStatus.Internal
    fun disable() {
        shouldAddMassTooltip = false
        idToBlockData.clear()
    }
}
