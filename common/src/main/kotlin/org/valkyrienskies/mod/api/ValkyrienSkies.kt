@file:JvmName("ValkyrienSkies")
package org.valkyrienskies.mod.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.Ship

val vsApi: VsApi @JvmName("getApi") get() = TODO()

fun Level?.getShipManagingBlock(pos: BlockPos?) = vsApi.getShipManagingBlock(this, pos)
fun Level?.getShipManagingBlock(x: Int, y: Int, z: Int) = getShipManagingChunk(x shr 4, z shr 4)

fun Level?.getLoadedShipManagingBlock(pos: BlockPos?): LoadedShip? = TODO()

fun Level?.getDeadShipManagingBlock(pos: BlockPos?): Ship? = TODO()

fun Level?.getShipManagingChunk(pos: ChunkPos?) = vsApi.getShipManagingChunk(this, pos)
fun Level?.getShipManagingChunk(chunkX: Int, chunkZ: Int) = vsApi.getShipManagingChunk(this, chunkX, chunkZ)
