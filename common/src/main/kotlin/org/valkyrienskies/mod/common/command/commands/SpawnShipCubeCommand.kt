package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import org.valkyrienskies.mod.common.assembly.ShipAssembler

object SpawnShipCubeCommand {

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(
            literal("perf-test").then(
                literal("spawn-ship-cube").then(
                    argument("size", IntegerArgumentType.integer(1, 20)).executes { ctx ->
                        val source = ctx.source
                        val player = source.playerOrException
                        val level = source.level as ServerLevel
                        val size = IntegerArgumentType.getInteger(ctx, "size")

                        // Compute spawn center: (size + 10) blocks in the player's look direction
                        val lookVec = player.lookAngle
                        val distance = (size + 10).toDouble()
                        val centerX = player.x + lookVec.x * distance
                        val centerY = player.y + lookVec.y * distance
                        val centerZ = player.z + lookVec.z * distance
                        val totalShips = size * size * size

                        source.sendSuccess({
                            Component.literal("[VS2] Spawning ${size}x${size}x${size} ship cube ($totalShips ships)...")
                        }, true)

                        val overallStart = System.currentTimeMillis()
                        val spacing = 2 // 1 block gap between each 1-block ship

                        // Phase 1: Place all iron blocks
                        val placeStart = System.currentTimeMillis()
                        val blockSets = mutableListOf<Set<BlockPos>>()
                        for (x in 0 until size) {
                            for (y in 0 until size) {
                                for (z in 0 until size) {
                                    val blockX = centerX.toInt() + (x - size / 2) * spacing
                                    val blockY = centerY.toInt() + (y - size / 2) * spacing
                                    val blockZ = centerZ.toInt() + (z - size / 2) * spacing
                                    val pos = BlockPos(blockX, blockY, blockZ)
                                    level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3)
                                    blockSets.add(hashSetOf(pos))
                                }
                            }
                        }
                        val placeMs = System.currentTimeMillis() - placeStart

                        // Phase 2: Batch-assemble all ships at once
                        // This is MUCH faster than sequential assembleToShip because:
                        // - One PacketStopChunkUpdates/Restart for ALL ships
                        // - One executeIf callback instead of N
                        // - Batched connectivity updates
                        val assembleStart = System.currentTimeMillis()
                        val results = try {
                            ShipAssembler.batchAssembleToShips(level, blockSets, 1.0)
                        } catch (e: Exception) {
                            source.sendFailure(Component.literal("[VS2] Batch assembly failed: ${e.message}"))
                            return@executes 0
                        }
                        val assembleMs = System.currentTimeMillis() - assembleStart

                        val totalMs = System.currentTimeMillis() - overallStart

                        source.sendSuccess({
                            Component.literal("[VS2] Created ${results.size} ships in ${totalMs}ms " +
                                "(place: ${placeMs}ms, " +
                                "assemble: ${assembleMs}ms, " +
                                "${totalMs / results.size}ms/ship)")
                        }, true)

                        results.size
                    }
                )
            )
        )
    }
}
