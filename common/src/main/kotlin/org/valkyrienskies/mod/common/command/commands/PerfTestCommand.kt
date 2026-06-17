package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.ClipContext.Block
import net.minecraft.world.level.ClipContext.Fluid
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import org.joml.Quaterniond
import org.joml.RoundingMode
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.internal.ships.VsiServerShip
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.yRange
import kotlin.math.ceil
import kotlin.math.sqrt

object PerfTestCommand {

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
            ).then(literal("jenga").then(
                argument("size", IntegerArgumentType.integer(1, 300)).executes { ctx ->
                    val source = ctx.source
                    val player = source.playerOrException
                    val level = source.level as ServerLevel
                    val size = IntegerArgumentType.getInteger(ctx, "size")

                    // raycast for target pos
                    val clipContext = ClipContext(
                        player.eyePosition,
                        player.eyePosition.add(player.lookAngle.multiply(10.0, 10.0, 10.0)),
                        Block.COLLIDER,
                        Fluid.ANY,
                        null
                    )
                    var target = level.clip(clipContext).blockPos

                    // Hacky fix so that we can raycast the floor without spawning in the floor
                    if (!level.getBlockState(target).isAir) {
                        target = target.above()
                    }

                    val totalShips = size * 3

                    source.sendSuccess({
                        Component.literal("[VS2] Spawning a $size tall jenga ($totalShips ships)...")
                    }, true)

                    val overallStart = System.currentTimeMillis()
                    val placeStart = System.currentTimeMillis()
                    val blockSets = mutableListOf<Set<BlockPos>>()

                    for (y in 0 until size) {
                        val isEvenLayer = (y % 2 == 0)

                        if (isEvenLayer) {
                            // Groups along X (each group spans Z)
                            for (x in 0 until 3) {
                                val tempShipSet = mutableSetOf<BlockPos>()
                                for (z in 0 until 3) {
                                    // Offset our center target by our current iteration coordinates
                                    val pos = BlockPos.containing(target.center.add(x.toDouble(), y.toDouble(), z.toDouble()))

                                    level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                                    tempShipSet.add(pos)
                                }

                                blockSets.add(tempShipSet)
                            }
                        } else {
                            // Groups along Z (each group spans X)
                            for (z in 0 until 3) {
                                val tempShipSet = mutableSetOf<BlockPos>()

                                for (x in 0 until 3) {
                                    // Offset our center target by our current iteration coordinates
                                    val pos = BlockPos.containing(target.center.add(x.toDouble(), y.toDouble(), z.toDouble()))

                                    level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3)
                                    tempShipSet.add(pos)
                                }

                                blockSets.add(tempShipSet)
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
            )).then(literal("end_ship").then(
                argument("count", IntegerArgumentType.integer(1, 1024)).executes { ctx ->
                    val source = ctx.source
                    val player = source.playerOrException
                    val level = source.level as ServerLevel
                    val count = IntegerArgumentType.getInteger(ctx, "count")

                    val template = level.structureManager
                        .get(ResourceLocation("end_city/ship"))
                        .orElse(null)
                    if (template == null) {
                        source.sendFailure(Component.literal("[VS2] Could not load end_city/ship structure template"))
                        return@executes 0
                    }
                    val size = template.size // Vec3i, footprint of the structure

                    val cols = ceil(sqrt(count.toDouble())).toInt()
                    val stepX = size.x + 4
                    val stepZ = size.z + 4
                    val lookVec = player.lookAngle
                    val baseX = player.x + lookVec.x * (stepX + 8)
                    val baseY = player.y
                    val baseZ = player.z + lookVec.z * (stepZ + 8)

                    source.sendSuccess({
                        Component.literal("[VS2] Spawning $count End City ships (${size.x}x${size.y}x${size.z} each)...")
                    }, true)

                    val overallStart = System.currentTimeMillis()
                    val random = RandomSource.create()
                    var created = 0

                    for (i in 0 until count) {
                        val gx = i % cols
                        val gz = i / cols
                        val worldPos = Vector3d(baseX + gx * stepX, baseY, baseZ + gz * stepZ)

                        val ship = level.shipObjectWorld.createNewShipAtBlock(
                            Vector3i(worldPos, RoundingMode.FLOOR), false, 1.0, level.dimensionId
                        )
                        ship.isStatic = true
                        EntityShipCollisionUtils.markShipAsRecentlySpawned(ship.id, level.server.tickCount.toLong())

                        val toCenter = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i())
                        val corner = BlockPos(
                            toCenter.x - size.x / 2,
                            toCenter.y - size.y / 2,
                            toCenter.z - size.z / 2
                        )
                        val settings = StructurePlaceSettings()
                        settings.rotationPivot = corner
                        template.placeInWorld(level, corner, corner, settings, random,
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS)

                        val placedBlocks = mutableListOf<BlockPos>()
                        for (dx in 0 until size.x) {
                            for (dy in 0 until size.y) {
                                for (dz in 0 until size.z) {
                                    val pos = corner.offset(dx, dy, dz)
                                    if (!level.getBlockState(pos).isAir) {
                                        placedBlocks.add(pos)
                                    }
                                }
                            }
                        }
                        if (placedBlocks.isEmpty()) {
                            level.shipObjectWorld.deleteShip(ship)
                            continue
                        }
                        ShipAssembler.initSkyLightForShip(level, placedBlocks)

                        val centerOfShip = Vector3d(
                            corner.x + size.x / 2.0,
                            corner.y + size.y / 2.0,
                            corner.z + size.z / 2.0
                        )
                        val posOffset = Vector3d(ship.inertiaData.centerOfMass).sub(centerOfShip)
                        (ship as VsiServerShip).unsafeSetKinematics(vsCore.newBodyKinematics(
                            Vector3d(),
                            Vector3d(),
                            vsCore.newBodyTransform(
                                Vector3d(worldPos).add(posOffset),
                                Quaterniond(),
                                Vector3d(1.0, 1.0, 1.0),
                                centerOfShip
                            )
                        ))
                        ship.isStatic = false
                        created++
                    }

                    val totalMs = System.currentTimeMillis() - overallStart
                    source.sendSuccess({
                        Component.literal("[VS2] Created $created End City ships in ${totalMs}ms" +
                            (if (created > 0) " (${totalMs / created}ms/ship)" else ""))
                    }, true)

                    created
                }
            ))
        )
    }
}
