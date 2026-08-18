package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
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

object ShootCommand {

    private const val MAX_SIZE = 48
    private const val IMPACT_GATE_MPS = 20.0 // native VoxelFractureConfig.fractureSpeed default

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(
            literal("shoot")
                .requires { it.hasPermission(2) }
                .then(
                    argument("size", IntegerArgumentType.integer(1, MAX_SIZE))
                        .then(
                            argument("speed", DoubleArgumentType.doubleArg(0.0, 2000.0))
                                // /vs shoot <size> <speed>   -> default material (obsidian)
                                .executes { ctx ->
                                    shoot(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        DoubleArgumentType.getDouble(ctx, "speed"),
                                        Blocks.OBSIDIAN
                                    )
                                }
                                .then(
                                    argument("material", ResourceLocationArgument.id())
                                        .suggests { _, builder ->
                                            SharedSuggestionProvider.suggestResource(
                                                BuiltInRegistries.BLOCK.keySet(), builder
                                            )
                                        }
                                        .executes { ctx ->
                                            val id = ResourceLocationArgument.getId(ctx, "material")
                                            val block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null)
                                            if (block == null) {
                                                ctx.source.sendFailure(Component.literal("Unknown block: $id"))
                                                return@executes 0
                                            }
                                            shoot(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                DoubleArgumentType.getDouble(ctx, "speed"),
                                                block
                                            )
                                        }
                                )
                        )
                )
        )
    }

    private fun shoot(ctx: CommandContext<CommandSourceStack>, size: Int, speed: Double, block: Block): Int {
        val source = ctx.source
        val level = source.level
        val shooter = source.entity
        if (shooter == null) {
            source.sendFailure(
                Component.literal("/vs shoot must be run by an entity (it fires from your eyes along your look direction).")
            )
            return 0
        }

        val look = shooter.lookAngle
        val dir = Vector3d(look.x, look.y, look.z)
        if (dir.lengthSquared() < 1.0e-9) dir.set(0.0, 0.0, 1.0) else dir.normalize()
        val eye = shooter.eyePosition
        val spawnDist = size * 1.5 + 4.0
        val spawnCenter = Vector3d(eye.x, eye.y, eye.z).add(dir.x * spawnDist, dir.y * spawnDist, dir.z * spawnDist)

        val ship = level.shipObjectWorld.createNewShipAtBlock(
            Vector3i(spawnCenter, RoundingMode.FLOOR), false, 1.0, level.dimensionId
        )
        EntityShipCollisionUtils.markShipAsRecentlySpawned(ship.id, level.server.tickCount.toLong())

        val claimCenter = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i())
        val half = size / 2
        val corner = BlockPos(claimCenter.x - half, claimCenter.y - half, claimCenter.z - half)
        val state: BlockState = block.defaultBlockState()
        val placed = ArrayList<BlockPos>(size * size * size)
        for (dx in 0 until size) {
            for (dy in 0 until size) {
                for (dz in 0 until size) {
                    val p = corner.offset(dx, dy, dz)
                    level.setBlock(p, state, Block.UPDATE_CLIENTS)
                    placed.add(p)
                }
            }
        }
        ShipAssembler.initSkyLightForShip(level, placed)

        val centerOfShip = Vector3d(corner.x + size / 2.0, corner.y + size / 2.0, corner.z + size / 2.0)
        val posOffset = Vector3d(ship.inertiaData.centerOfMass).sub(centerOfShip)
        val velocity = Vector3d(dir).mul(speed)
        (ship as VsiServerShip).unsafeSetKinematics(
            vsCore.newBodyKinematics(
                velocity,
                Vector3d(),
                vsCore.newBodyTransform(
                    Vector3d(spawnCenter).add(posOffset),
                    Quaterniond(),
                    Vector3d(1.0, 1.0, 1.0),
                    centerOfShip
                )
            )
        )

        val id = BuiltInRegistries.BLOCK.getKey(block)
        source.sendSuccess({
            val base = "Fired ${size}x${size}x${size} $id ship #${ship.id} at ${"%.1f".format(speed)} m/s"
            if (speed < IMPACT_GATE_MPS) {
                Component.literal("$base  (note: impact fracture needs ~${IMPACT_GATE_MPS.toInt()}+ m/s to trigger)")
            } else {
                Component.literal(base)
            }
        }, true)
        return 1
    }
}
