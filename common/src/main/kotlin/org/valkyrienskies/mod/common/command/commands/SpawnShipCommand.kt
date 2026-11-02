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
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.storage.LevelResource
import org.joml.Quaterniond
import org.joml.RoundingMode
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.joints.VSJointPose
import org.valkyrienskies.core.internal.joints.VSRevoluteJoint
import org.valkyrienskies.core.internal.ships.VsiServerShip
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.schematic.VdexIO
import org.valkyrienskies.mod.common.schematic.VdexShipEntry
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.yRange
import java.nio.file.Files

object SpawnShipCommand {

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("spawn-ship")
            .requires { it.hasPermission(2) }
            .then(argument("filename", StringArgumentType.word())
                .executes { ctx ->
                    spawnShip(ctx, StringArgumentType.getString(ctx, "filename"))
                }
            )
        )
    }

    private fun spawnShip(ctx: CommandContext<CommandSourceStack>, filename: String): Int {
        val level = ctx.source.level
        val spawnPos = Vector3d(ctx.source.position.x, ctx.source.position.y, ctx.source.position.z)

        val worldDir = level.server.getWorldPath(LevelResource.ROOT)
        val filePath = worldDir.resolve("schematics").resolve("$filename.vdex")

        if (!Files.exists(filePath)) {
            ctx.source.sendFailure(Component.literal("File not found: $filename.vdex"))
            return 0
        }

        val vdex = VdexIO.read(filePath)
        val metadata = vdex.metadata

        if (metadata.ships.isEmpty()) {
            ctx.source.sendFailure(Component.literal("Schematic contains no ships"))
            return 0
        }

        val spawnedShips = mutableListOf<ServerShip>()

        for (idx in metadata.ships.indices) {
            val shipEntry = metadata.ships[idx]
            val nbtTag = vdex.nbtData[shipEntry.nbtFile]
            if (nbtTag == null) {
                ctx.source.sendFailure(Component.literal("Missing NBT data: ${shipEntry.nbtFile}"))
                return 0
            }

            val ship = spawnSingleShip(level, nbtTag, shipEntry, spawnPos, idx)
            if (ship == null) {
                ctx.source.sendFailure(Component.literal("Failed to spawn ship ${shipEntry.name}"))
                return 0
            }
            spawnedShips.add(ship)
        }

        // Re-create constraints
        if (metadata.constraints.isNotEmpty()) {
            val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
            for (constraint in metadata.constraints) {
                if (constraint.shipIndex0 >= spawnedShips.size || constraint.shipIndex1 >= spawnedShips.size) continue

                val ship0 = spawnedShips[constraint.shipIndex0]
                val ship1 = spawnedShips[constraint.shipIndex1]

                val joint = VSRevoluteJoint(
                    ship0.id,
                    VSJointPose(constraint.pose0.toPosition(), constraint.pose0.toRotation()),
                    ship1.id,
                    VSJointPose(constraint.pose1.toPosition(), constraint.pose1.toRotation()),
                    maxForceTorque = null,
                    driveFreeSpin = true
                )

                gtpa.addJoint(joint, delay = 4) { _ -> }
            }
        }

        ctx.source.sendSuccess({
            Component.literal("Spawned ${spawnedShips.size} ship(s) from $filename.vdex")
        }, true)
        return 1
    }

    private fun spawnSingleShip(
        level: ServerLevel,
        nbtTag: CompoundTag,
        entry: VdexShipEntry,
        spawnPos: Vector3d,
        index: Int
    ): ServerShip? {
        // Load the structure template from saved NBT
        val template = StructureTemplate()
        template.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), nbtTag)

        val size = template.size
        if (size.x == 0 && size.y == 0 && size.z == 0) return null

        // Calculate world position for this ship (main ship at spawnPos, others offset)
        val worldPos = Vector3d(spawnPos).add(entry.relativeX, entry.relativeY, entry.relativeZ)

        // Create the ship
        val ship = level.shipObjectWorld.createNewShipAtBlock(
            Vector3i(worldPos, RoundingMode.FLOOR), false, entry.scale, level.dimensionId
        )
        ship.isStatic = entry.isStatic

        EntityShipCollisionUtils.markShipAsRecentlySpawned(
            ship.id, level.server.tickCount.toLong()
        )

        // Place blocks in the ship's chunk claim
        val toCenter = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i())

        // Calculate corner position so the structure is centered in the claim
        val halfSize = Vector3d(size.x / 2.0, size.y / 2.0, size.z / 2.0)
        val cornerOfShip = BlockPos(
            toCenter.x - halfSize.x.toInt(),
            toCenter.y - halfSize.y.toInt(),
            toCenter.z - halfSize.z.toInt()
        )

        val settings = StructurePlaceSettings()
        settings.rotationPivot = cornerOfShip
        template.placeInWorld(level, cornerOfShip, cornerOfShip, settings, level.random, Block.UPDATE_CLIENTS)

        // Compute the center of the placed structure for kinematics
        val centerOfShip = Vector3d(
            cornerOfShip.x + halfSize.x,
            cornerOfShip.y + halfSize.y,
            cornerOfShip.z + halfSize.z
        )

        // Initialize sky light for the placed blocks
        val placedBlocks = mutableListOf<BlockPos>()
        for (dx in 0 until size.x) {
            for (dy in 0 until size.y) {
                for (dz in 0 until size.z) {
                    val pos = cornerOfShip.offset(dx, dy, dz)
                    if (!level.getBlockState(pos).isAir) {
                        placedBlocks.add(pos)
                    }
                }
            }
        }
        ShipAssembler.initSkyLightForShip(level, placedBlocks)

        // Set kinematics so the ship appears at the world position
        val posOffset = Vector3d(ship.inertiaData.centerOfMass).sub(centerOfShip)
        (ship as VsiServerShip).unsafeSetKinematics(vsCore.newBodyKinematics(
            Vector3d(),
            Vector3d(),
            vsCore.newBodyTransform(
                Vector3d(worldPos).add(posOffset),
                Quaterniond(),
                Vector3d(entry.scale, entry.scale, entry.scale),
                centerOfShip
            )
        ))

        return ship
    }
}
