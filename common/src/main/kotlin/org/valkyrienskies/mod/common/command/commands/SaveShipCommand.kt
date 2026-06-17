package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.storage.LevelResource
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.schematic.VdexConstraintEntry
import org.valkyrienskies.mod.common.schematic.VdexIO
import org.valkyrienskies.mod.common.schematic.VdexJointPose
import org.valkyrienskies.mod.common.schematic.VdexMetadata
import org.valkyrienskies.mod.common.schematic.VdexShipEntry
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.util.StructureTemplateFillFromVoxelSet
import java.nio.file.Files

object SaveShipCommand {

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("save-ship")
            .requires { it.hasPermission(2) }
            .then(argument("ship", ShipArgument.ships())
                .then(argument("filename", StringArgumentType.word())
                    .executes { ctx ->
                        saveShip(ctx, ShipArgument.getShips(ctx, "ship").toList() as List<ServerShip>,
                            StringArgumentType.getString(ctx, "filename"))
                    }
                )
            )
        )
    }

    private fun saveShip(ctx: CommandContext<CommandSourceStack>, ships: List<ServerShip>, filename: String): Int {
        val level = ctx.source.level
        val mainShip = ships.firstOrNull()
            ?: run { ctx.source.sendFailure(Component.literal("No ship selected")); return 0 }

        // Find all connected ships via constraints
        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
        val connectedIds = gtpa.getAllConnectedShips(mainShip.id)
        val allShips = connectedIds.mapNotNull { id ->
            level.shipObjectWorld.allShips.getById(id) as? ServerShip
        }

        // Main ship is index 0; find its index in the connected set
        val mainShipIndex = allShips.indexOfFirst { it.id == mainShip.id }.coerceAtLeast(0)

        // Reorder so main ship is first
        val orderedShips = mutableListOf<ServerShip>()
        orderedShips.add(allShips[mainShipIndex])
        for (i in allShips.indices) {
            if (i != mainShipIndex) orderedShips.add(allShips[i])
        }

        val mainPos = orderedShips[0].transform.position
        val shipEntries = mutableListOf<VdexShipEntry>()
        val nbtData = mutableMapOf<String, CompoundTag>()

        for (idx in orderedShips.indices) {
            val ship = orderedShips[idx]
            val nbtFileName = "ship_$idx.nbt"
            val blocks = collectShipBlocks(level, ship)
            if (blocks.isEmpty()) continue

            val minMax = ShipAssembler.findMinAndMax(blocks)
            val minB = minMax.first
            val maxB = minMax.second
            val template = StructureTemplate()
            (template as StructureTemplateFillFromVoxelSet).`vs$fillFromVoxelSet`(
                level, blocks, listOf(ship),
                ShipAssembler.SingleItemMap(ship.id, Vector3d(), Vector3d()),
                minB, maxB
            )

            val tag = template.save(CompoundTag())
            nbtData[nbtFileName] = tag

            val relPos = Vector3d(ship.transform.position).sub(mainPos)
            shipEntries.add(VdexShipEntry(
                name = ship.slug ?: "ship_$idx",
                nbtFile = nbtFileName,
                relativeX = relPos.x,
                relativeY = relPos.y,
                relativeZ = relPos.z,
                isStatic = ship.isStatic,
                scale = ship.transform.scaling.x()
            ))
        }

        // Build ship ID -> index map for constraint references
        val shipIdToIndex = mutableMapOf<Long, Int>()
        for (idx in orderedShips.indices) {
            shipIdToIndex[orderedShips[idx].id] = idx
        }

        // Collect constraints between ships in the set
        val constraintEntries = mutableListOf<VdexConstraintEntry>()
        val seenJoints = mutableSetOf<Any>()
        for (ship in orderedShips) {
            val jointIds = gtpa.getJointsFromShip(ship.id) ?: continue
            for (jointId in jointIds) {
                if (!seenJoints.add(jointId)) continue
                val joint = gtpa.getJointById(jointId) ?: continue
                val idx0 = shipIdToIndex[joint.shipId0] ?: continue
                val idx1 = shipIdToIndex[joint.shipId1] ?: continue

                constraintEntries.add(VdexConstraintEntry(
                    type = joint.javaClass.simpleName,
                    shipIndex0 = idx0,
                    shipIndex1 = idx1,
                    pose0 = VdexJointPose.fromPosRot(
                        Vector3d(joint.pose0.pos), Quaterniond(joint.pose0.rot)
                    ),
                    pose1 = VdexJointPose.fromPosRot(
                        Vector3d(joint.pose1.pos), Quaterniond(joint.pose1.rot)
                    )
                ))
            }
        }

        val metadata = VdexMetadata(
            version = 1,
            mainShipIndex = 0,
            ships = shipEntries,
            constraints = constraintEntries
        )

        // Save to world/schematics/ directory
        val worldDir = level.server.getWorldPath(LevelResource.ROOT)
        val schematicsDir = worldDir.resolve("schematics")
        Files.createDirectories(schematicsDir)
        val filePath = schematicsDir.resolve("$filename.vdex")

        VdexIO.write(filePath, metadata, nbtData)

        ctx.source.sendSuccess({
            Component.literal("Saved ${orderedShips.size} ship(s) to $filename.vdex (${constraintEntries.size} constraints)")
        }, true)
        return 1
    }

    private fun collectShipBlocks(level: ServerLevel, ship: ServerShip): List<BlockPos> {
        val blocks = mutableListOf<BlockPos>()
        if (ship !is LoadedServerShip) return blocks

        ship.activeChunksSet.forEach { cx, cz ->
            val chunk = level.getChunk(cx, cz)
            val sections = chunk.sections
            for (sIdx in sections.indices) {
                val section = sections[sIdx] ?: continue
                if (section.hasOnlyAir()) continue
                val baseY = chunk.getSectionYFromSectionIndex(sIdx) shl 4
                for (lx in 0..15) {
                    for (ly in 0..15) {
                        for (lz in 0..15) {
                            val state = section.getBlockState(lx, ly, lz)
                            if (!state.isAir) {
                                blocks.add(BlockPos(
                                    (cx shl 4) + lx,
                                    baseY + ly,
                                    (cz shl 4) + lz
                                ))
                            }
                        }
                    }
                }
            }
        }
        return blocks
    }
}
