package org.valkyrienskies.mod.common

import com.mojang.serialization.Lifecycle
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.RegistryKey
import net.minecraft.util.registry.SimpleRegistry
import net.minecraft.world.World
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MASS_DATAPACK_RESOLVER

// Other mods can then provide weights and types based on their added content
interface BlockStateInfoProvider {
    val priority: Int

    fun getBlockStateMass(blockState: BlockState): Double?

    fun getBlockStateType(blockState: BlockState): VSBlockType?
}

object BlockStateInfo {

    // registry for mods to add their weights
    val REGISTRY = SimpleRegistry<BlockStateInfoProvider>(
        RegistryKey.ofRegistry(Identifier(ValkyrienSkiesMod.MOD_ID, "blockstate_providers")),
        Lifecycle.experimental()
    )

    init {
        Registry.register(REGISTRY, Identifier(ValkyrienSkiesMod.MOD_ID, "data"), MASS_DATAPACK_RESOLVER)
        Registry.register(REGISTRY, Identifier(ValkyrienSkiesMod.MOD_ID, "default"), DefaultBlockStateInfoProvider)
    }

    // no clue about these different Int2ObjectMaps just took a random one

    val CACHE = Int2ObjectAVLTreeMap<Pair<Double, VSBlockType>>()
    // NOTE: this caching can get allot better, ex. default just returns constants so it might be more faster
    //  if we store that these values do not need to be cached by double and blocktype but just that they use default impl

    // this gets the weight and type provided by providers; or it gets it out of the cache

    private fun get(blockState: BlockState): Pair<Double, VSBlockType> =
        CACHE.get(Block.getRawIdFromState(blockState)) ?: iterateRegistry(blockState)

    private fun iterateRegistry(blockState: BlockState): Pair<Double, VSBlockType> =
        Pair(
            // TODO maybe i should sort the registry; instead of sorting it on the fly :P
            REGISTRY.sortedByDescending { it.priority }.mapNotNull { it.getBlockStateMass(blockState) }.first(),
            REGISTRY.sortedByDescending { it.priority }.mapNotNull { it.getBlockStateType(blockState) }.first(),
        )

    // NOTE: this gets called irrelevant if the block is actually on a ship; so it needs to be changed that
    // shipObjectWorld only requests the data if needed (maybe supplier?)
    fun onSetBlock(world: World, blockPos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) {
        val shipObjectWorld = world.shipObjectWorld

        val (prevBlockMass, prevBlockType) = get(prevBlockState)

        val (newBlockMass, newBlockType) = get(newBlockState)

        shipObjectWorld.onSetBlock(
            blockPos.x, blockPos.y, blockPos.z, prevBlockType, newBlockType, prevBlockMass, newBlockMass
        )
    }
}
