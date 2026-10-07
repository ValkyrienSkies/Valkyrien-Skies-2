package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.TargetBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.gameevent.GameEvent
import org.joml.Vector3d
import org.joml.Matrix4d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.events.CollisionEvent
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getEnclosingShip
import org.valkyrienskies.mod.common.getShipMountedTo
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.mixin.feature.ship_interactions.PressurePlateAccessor
import org.valkyrienskies.mod.mixin.feature.ship_interactions.ButtonAccessor
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object ShipInteractions {
    private val fragile = TagKey.create(Registries.BLOCK, ResourceLocation("valkyrienskies", "fragile_ship_collision"))
    private val damageType = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation("valkyrienskies", "ship_impact"))
    private data class ForceSettings(val enabled: Boolean, val duration: Double, val minSpeed: Double)
    @Volatile private var forceSettings = ForceSettings(true, 0.05, 4.0)
    private data class ImpactKey(val dimension: String, val ship: Long?, val other: Long?,
        val x: Int, val y: Int, val z: Int)
    private val impacts = ConcurrentHashMap<ImpactKey, ShipFragileBlocks.Impact>()
    private val damageTimes = WeakHashMap<net.minecraft.world.entity.player.Player, Long>()
    private val pressedPlates = WeakHashMap<ServerLevel, MutableSet<BlockPos>>()

    @JvmStatic
    fun onCollision(event: CollisionEvent) {
        val settings = forceSettings
        if (!settings.enabled) return
        val a = event.physLevel.getShipById(event.shipIdA)
        val b = event.physLevel.getShipById(event.shipIdB)
        val massA = if (a == null || a.isStatic) 0.0 else a.mass
        val massB = if (b == null || b.isStatic) 0.0 else b.mass
        val bodies = listOf(a, b)
        val transforms = bodies.map { it?.worldToShip?.let(::Matrix4d) ?: Matrix4d() }
        val toOther = transforms.indices.map { index ->
            Matrix4d(transforms[1 - index]).mul(Matrix4d(transforms[index]).invert())
        }
        for (contact in event.contactPoints) {
            val speed = ShipInteractionMath.closingSpeed(contact.velocity, contact.normal)
            if (!contact.separation.isFinite()) continue
            val force = ShipInteractionMath.impactForce(massA, massB, speed, settings.duration, settings.minSpeed)
            if (force <= 0.0 || !force.isFinite() || !contact.position.isFinite) continue
            if (impacts.size >= 8192) break
            for ((index, body) in bodies.withIndex()) {
                val p = Vector3d(contact.position)
                val normal = Vector3d(contact.normal).mul(if (index == 0) 1.0 else -1.0)
                // Keep the contact in the ship frame while the ship moves.
                transforms[index].transformPosition(p)
                transforms[index].transformDirection(normal)
                val other = bodies[1 - index]
                val key = ImpactKey(event.dimensionId, body?.id, other?.id,
                    floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
                val copy = ShipFragileBlocks.Impact(p, normal, toOther[index], force)
                impacts.merge(key, copy) { old, new -> if (new.force > old.force) new else old }
            }
        }
    }

    @JvmStatic
    fun clear() {
        impacts.clear()
        damageTimes.clear()
        pressedPlates.clear()
    }

    @JvmStatic
    fun tick(level: ServerLevel) {
        val config = VSGameConfig.SERVER.ShipInteractions
        forceSettings = ForceSettings(config.breakFragileBlocks, config.impactDuration, config.fragileBlockMinImpactSpeed)
        val pending = HashMap<Pair<Long?, Long?>, MutableList<ShipFragileBlocks.Impact>>()
        for ((key, impact) in impacts.entries) {
            if (key.dimension != level.dimensionId || !impacts.remove(key, impact)) continue
            if (!config.breakFragileBlocks || impact.force < config.fragileBlockForce) continue
            val ship = key.ship?.let { level.shipObjectWorld.loadedShips.getById(it) }
            if (key.ship != null && ship == null) continue
            if (key.other != null && level.shipObjectWorld.loadedShips.getById(key.other) == null) continue
            pending.getOrPut(key.ship to key.other) { ArrayList() }.add(impact)
        }
        val broken = HashSet<BlockPos>()
        val budget = ShipFragileBlocks.Budget()
        for ((pair, contacts) in pending) {
            broken.addAll(ShipFragileBlocks.blocksToBreak(level, contacts, config.fragileBlockForce,
                { it.`is`(fragile) },
                {
                    if (!level.hasChunkAt(it)) { budget.remaining = 0; false }
                    else level.getShipManagingPos(it)?.id == pair.first
                },
                {
                    if (!level.hasChunkAt(it)) { budget.remaining = 0; false }
                    else level.getShipManagingPos(it)?.id == pair.second
                }, pair.first != null, budget))
        }
        for (pos in broken) {
            val state = level.getBlockState(pos)
            if (state.`is`(fragile) && state.getDestroySpeed(level, pos) >= 0.0f) level.destroyBlock(pos, true)
        }
        if (config.shipRedstone) tickRedstone(level)
        else {
            pressedPlates.remove(level)?.forEach { pos ->
                val block = level.getBlockState(pos).block
                if (block is BasePressurePlateBlock) level.scheduleTick(pos, block, 1)
            }
        }
        if (config.shipImpactDamage) tickPlayerDamage(level)
    }

    private fun isSensor(state: BlockState): Boolean = state.block is BasePressurePlateBlock ||
        state.block is ButtonBlock || state.block is LeverBlock || state.block is TargetBlock

    private fun tickRedstone(level: ServerLevel) {
        val plates = HashSet<BlockPos>()
        val visited = HashSet<Pair<Long, BlockPos>>()
        for (source in level.shipObjectWorld.loadedShips) {
            if (source.chunkClaimDimension != level.dimensionId) continue
            val swept = ShipBlockContacts.sweptBounds(source)
            scanSensors(level, swept, null) { pos ->
                if (visited.add(source.id to pos)) operate(level, pos, source, null, plates)
            }
            for (target in level.shipObjectWorld.loadedShips.getIntersecting(swept, level.dimensionId)) {
                if (target.id == source.id) continue
                scanSensors(level, AABBd(swept).transform(target.worldToShip), target) { pos ->
                    if (visited.add(source.id to pos)) operate(level, pos, source, target, plates)
                }
            }
        }
        val previous = pressedPlates.put(level, plates) ?: emptySet()
        for (pos in previous) if (pos !in plates && level.hasChunkAt(pos)) {
            val block = level.getBlockState(pos).block
            if (block is BasePressurePlateBlock) level.scheduleTick(pos, block, 1)
        }
    }

    private fun scanSensors(level: ServerLevel, bounds: AABBd, ship: Ship?, action: (BlockPos) -> Unit) {
        val minX = floor(bounds.minX - 0.25).toInt()
        val maxX = floor(bounds.maxX + 0.25).toInt()
        val minZ = floor(bounds.minZ - 0.25).toInt()
        val maxZ = floor(bounds.maxZ + 0.25).toInt()
        val minY = max(level.minBuildHeight, floor(bounds.minY - 0.25).toInt())
        val maxY = min(level.maxBuildHeight - 1, floor(bounds.maxY + 0.25).toInt())
        var sectionCount = 0
        for (cx in (minX shr 4)..(maxX shr 4)) for (cz in (minZ shr 4)..(maxZ shr 4)) {
            if (ship != null && !ship.activeChunksSet.contains(cx, cz)) continue
            val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
            for (sy in (minY shr 4)..(maxY shr 4)) {
                if (++sectionCount > 2048) return
                val section = chunk.getSection(chunk.getSectionIndex(sy shl 4))
                if (!section.maybeHas(::isSensor)) continue
                for (x in max(minX, cx shl 4)..min(maxX, (cx shl 4) + 15))
                    for (z in max(minZ, cz shl 4)..min(maxZ, (cz shl 4) + 15))
                        for (y in max(minY, sy shl 4)..min(maxY, (sy shl 4) + 15)) {
                            if (isSensor(section.getBlockState(x and 15, y and 15, z and 15))) action(BlockPos(x, y, z))
                        }
            }
        }
    }

    private fun operate(level: ServerLevel, pos: BlockPos, source: Ship, target: Ship?, plates: MutableSet<BlockPos>) {
        val state = level.getBlockState(pos)
        if (level.getShipManagingPos(pos)?.id != target?.id) return
        val block = state.block
        val shape = if (block is BasePressurePlateBlock) {
            AABBd(pos.x + 0.125, pos.y.toDouble(), pos.z + 0.125, pos.x + 0.875, pos.y + 0.25, pos.z + 0.875)
        } else {
            val shape = state.getShape(level, pos)
            if (shape.isEmpty) return
            shape.bounds().move(pos).toJOML()
        }
        val contact = ShipBlockContacts.find(level, source, shape, target, block !is BasePressurePlateBlock) ?: return
        val localVelocity = Vector3d(contact.velocity)
        target?.transform?.rotation?.transformInverse(localVelocity)
        when (block) {
            is BasePressurePlateBlock -> {
                plates.add(pos)
                val accessor = block as PressurePlateAccessor
                accessor.`vs$checkPressed`(null, level, pos, state, accessor.`vs$getSignalForState`(state))
            }
            is ButtonBlock -> {
                if (!state.getValue(ButtonBlock.POWERED) && localVelocity.dot(outward(state)) < -0.1) {
                    block.press(state, level, pos)
                    (block as ButtonAccessor).`vs$playSound`(null, level, pos, true)
                }
            }
            is LeverBlock -> {
                val axis = if (state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL)
                    Vector3d(0.0, 1.0, 0.0) else state.getValue(BlockStateProperties.HORIZONTAL_FACING).normal.toJOMLD()
                val push = localVelocity.dot(axis)
                if (abs(push) > VSGameConfig.SERVER.ShipInteractions.leverPushSpeed) {
                    val powered = push < 0.0
                    if (state.getValue(LeverBlock.POWERED) != powered) {
                        block.pull(state, level, pos)
                        level.gameEvent(null, if (powered) GameEvent.BLOCK_ACTIVATE else GameEvent.BLOCK_DEACTIVATE, pos)
                        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                            0.3f, if (powered) 0.6f else 0.5f)
                    }
                }
            }
            is TargetBlock -> {
                val speed = max(0.0, localVelocity.dot(contact.normal))
                val threshold = VSGameConfig.SERVER.ShipInteractions.targetImpactSpeed
                if (speed >= threshold && speed > 0.0 && !level.blockTicks.hasScheduledTick(pos, block)) {
                    val power = (speed / max(0.1, threshold) * 5.0).toInt().coerceIn(1, 15)
                    level.setBlock(pos, state.setValue(BlockStateProperties.POWER, power), Block.UPDATE_ALL)
                    level.scheduleTick(pos, block, 8)
                    level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, pos)
                }
            }
        }
    }

    private fun outward(state: BlockState): Vector3d = when (state.getValue(BlockStateProperties.ATTACH_FACE)) {
        AttachFace.FLOOR -> Direction.UP.normal.toJOMLD()
        AttachFace.CEILING -> Direction.DOWN.normal.toJOMLD()
        else -> state.getValue(BlockStateProperties.HORIZONTAL_FACING).normal.toJOMLD()
    }

    @JvmStatic
    fun plateSignal(level: net.minecraft.world.level.Level, pos: BlockPos): Int {
        if (level !is ServerLevel || !VSGameConfig.SERVER.ShipInteractions.shipRedstone) return 0
        val target = level.getShipManagingPos(pos)
        val sensor = AABBd(pos.x + 0.125, pos.y.toDouble(), pos.z + 0.125, pos.x + 0.875, pos.y + 0.25, pos.z + 0.875)
        val world = if (target != null) AABBd(sensor).transform(target.shipToWorld) else sensor
        for (source in level.shipObjectWorld.loadedShips.getIntersecting(world, level.dimensionId)) {
            if (ShipBlockContacts.find(level, source, sensor, target, false) != null) return 15
        }
        return 0
    }

    private fun tickPlayerDamage(level: ServerLevel) {
        val config = VSGameConfig.SERVER.ShipInteractions
        for (player in level.players()) {
            if (player.isSpectator || player.isCreative || player.isDeadOrDying) continue
            val last = damageTimes[player]
            if (last != null && level.gameTime - last in 0 until config.playerImpactCooldownTicks.coerceAtLeast(1).toLong()) continue
            var worst = 0.0
            val rider = getShipMountedTo(player)?.id ?: player.getEnclosingShip()?.id
            for (ship in level.shipObjectWorld.loadedShips) {
                if (ship.chunkClaimDimension != level.dimensionId || ship.id == rider) continue
                if (!ShipBlockContacts.sweptBounds(ship).intersectsAABB(player.boundingBox.toJOML())) continue
                val contact = ShipBlockContacts.find(level, ship, player.boundingBox.toJOML()) ?: continue
                val relative = contact.velocity.sub(player.deltaMovement.toJOML().mul(20.0))
                worst = max(worst, relative.dot(contact.normal))
            }
            val damage = ShipInteractionMath.impactDamage(worst, config.playerImpactSpeed,
                config.playerImpactDamageScale, config.maxPlayerImpactDamage)
            if (damage > 0.0f) {
                val source = DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(damageType))
                if (player.hurt(source, damage)) damageTimes[player] = level.gameTime
            }
        }
    }
}
