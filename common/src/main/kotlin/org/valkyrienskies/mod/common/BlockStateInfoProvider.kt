package org.valkyrienskies.mod.common

import com.mojang.serialization.Lifecycle
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.apigame.world.chunks.BlockType
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.event.RegistryEvents

// Other mods can then provide weights and types based on their added content
// NOTE: if we have block's in vs-core we should ask getVSBlock(blockstate: BlockStat): VSBlock since thatd be more handy
//  altough we might want to allow null properties in VSBlock that is returned since we do want partial data fetching
// https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/25
interface BlockStateInfoProvider {
    val priority: Int

    fun getBlockStateMass(blockState: BlockState): Double?

    fun getBlockStateType(blockState: BlockState): BlockType?
}

object BlockStateInfo {

    // registry for mods to add their weights
    val REGISTRY = MappedRegistry<BlockStateInfoProvider>(
        ResourceKey.createRegistryKey(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "blockstate_providers")),
        Lifecycle.experimental(),
        null
    )

    private lateinit var SORTED_REGISTRY: List<BlockStateInfoProvider>

    // init { doesn't work since the class gets loaded too late
    fun init() {
        Registry.register(REGISTRY, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "data"), MassDatapackResolver)
        Registry.register(
            REGISTRY, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "default"), DefaultBlockStateInfoProvider
        )

        RegistryEvents.onRegistriesComplete { SORTED_REGISTRY = REGISTRY.sortedByDescending { it.priority } }
    }

    // This is [ThreadLocal] because in single-player games the Client thread and Server thread will read/write to
    // [CACHE] simultaneously. This creates a data race that can crash the game (See https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/126).
    val CACHE: ThreadLocal<Int2ObjectOpenHashMap<Pair<Double, BlockType>>> =
        ThreadLocal.withInitial { Int2ObjectOpenHashMap<Pair<Double, BlockType>>() }
    // NOTE: this caching can get allot better, ex. default just returns constants so it might be more faster
    //  if we store that these values do not need to be cached by double and blocktype but just that they use default impl

    // this gets the weight and type provided by providers; or it gets it out of the cache

    fun get(blockState: BlockState): Pair<Double, BlockType>? =
        getId(blockState)?.let { CACHE.get().getOrPut(it) { iterateRegistry(blockState) } }

    fun getId(blockState: BlockState): Int? {
        val r = Block.getId(blockState)
        if (r == -1) return null
        return r
    }

    private fun iterateRegistry(blockState: BlockState): Pair<Double, BlockType> =
        Pair(
            SORTED_REGISTRY.firstNotNullOf { it.getBlockStateMass(blockState) },
            SORTED_REGISTRY.firstNotNullOf { it.getBlockStateType(blockState) },
        )

    // NOTE: this gets called irrelevant if the block is actually on a ship; so it needs to be changed that
    // shipObjectWorld only requests the data if needed (maybe supplier?)
    // NOTE2: spoken of in discord in the future well have prob block's in vs-core with id's and then
    // the above issue shall be fixed
    // https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/25

    fun onSetBlock(level: Level, blockPos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) =
        onSetBlock(level, blockPos.x, blockPos.y, blockPos.z, prevBlockState, newBlockState)

    fun onSetBlock(level: Level, x: Int, y: Int, z: Int, prevBlockState: BlockState, newBlockState: BlockState) {
        if (!::SORTED_REGISTRY.isInitialized) return

        val shipObjectWorld = level.shipObjectWorld

        val (prevBlockMass, prevBlockType) = get(prevBlockState) ?: return

        val (newBlockMass, newBlockType) = get(newBlockState) ?: return

        shipObjectWorld.onSetBlock(
            x, y, z, level.dimensionId, prevBlockType, newBlockType, prevBlockMass,
            newBlockMass
        )
    }
}
