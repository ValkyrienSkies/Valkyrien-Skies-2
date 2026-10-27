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
    private data class PlayerFrame(val dimension: String, val bounds: AABBd, val velocity: Vector3d, val rider: Long?)
    private val playerFrames = WeakHashMap<net.minecraft.world.entity.player.Player, PlayerFrame>()

    @JvmStatic
    fun onCollision(event: CollisionEvent) {
        val settings = forceSettings
        if (!settings.enabled) return
        val a = event.physLevel.getShipById(event.shipIdA)
        val b = event.physLevel.getShipById(event.shipIdB)
        val massA = if (a == null || a.isStatic) 0.0 else a.mass
        val massB = if (b == null || b.isStatic) 0.0 else b.mass
        val bodies = listOf(a, b)
        var transforms: List<Matrix4d>? = null
        var toOther: List<Matrix4d>? = null
        for (contact in event.contactPoints) {
            val speed = ShipImpactVelocities.closingSpeed(event, contact)
            if (!contact.separation.isFinite()) continue
            val force = ShipInteractionMath.impactForce(massA, massB, speed, settings.duration, settings.minSpeed)
            if (force <= 0.0 || !force.isFinite() || !contact.position.isFinite) continue
            if (impacts.size >= 8192) break
            val contactTransforms = transforms ?: bodies.map {
                it?.worldToShip?.let(::Matrix4d) ?: Matrix4d()
            }.also { transforms = it }
            val otherTransforms = toOther ?: contactTransforms.indices.map { index ->
                Matrix4d(contactTransforms[1 - index]).mul(Matrix4d(contactTransforms[index]).invert())
            }.also { toOther = it }
            for ((index, body) in bodies.withIndex()) {
                val p = Vector3d(contact.position)
                val normal = Vector3d(contact.normal).mul(if (index == 0) 1.0 else -1.0)
                // Keep the contact in the ship frame while the ship moves.
                contactTransforms[index].transformPosition(p)
                contactTransforms[index].transformDirection(normal)
                val other = bodies[1 - index]
                val key = ImpactKey(event.dimensionId, body?.id, other?.id,
                    floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
                val copy = ShipFragileBlocks.Impact(p, normal, otherTransforms[index], force)
                impacts.merge(key, copy) { old, new -> if (new.force > old.force) new else old }
            }
        }
    }

    @JvmStatic
    fun clear() {
        ShipPushing.clear()
        ShipImpactVelocities.clear()
        impacts.clear()
        damageTimes.clear()
        playerFrames.clear()
        ShipPressurePlates.clear()
        ShipButtons.clear()
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
    }

    /** Check contacts after the core applies the new ship positions. */
    @JvmStatic
    fun afterPhysicsTick(level: ServerLevel) {
        ShipPushing.tick(level)
        val config = VSGameConfig.SERVER.ShipInteractions
        if (config.shipImpactDamage) tickPlayerDamage(level)
        else playerFrames.clear()
        if (config.shipRedstone) tickRedstone(level)
        else {
            ShipButtons.update(level, emptyMap())
            ShipPressurePlates.update(level, emptyMap())
        }
    }

    private fun playerFrame(player: net.minecraft.world.entity.player.Player, level: ServerLevel): PlayerFrame {
        val bounds = player.boundingBox.toJOML()
        val mounted = getShipMountedTo(player)?.id
        val dragged = player.getEnclosingShip()
        val feet = AABBd(bounds.minX, bounds.minY - .05, bounds.minZ,
            bounds.maxX, bounds.minY + .05, bounds.maxZ)
        // Recent ship contact does not make the player a rider of that ship.
        val supported = dragged != null && ShipBlockContacts.find(level, dragged, feet, sweep = false,
            pushNormal = Vector3d(0.0, 1.0, 0.0)) != null
        return PlayerFrame(level.dimensionId, bounds, player.deltaMovement.toJOML().mul(20.0),
            mounted ?: dragged?.id?.takeIf { supported })
    }

    /** Save the first player position before the ship positions change. */
    @JvmStatic
    fun beginTick(level: ServerLevel) {
        if (!VSGameConfig.SERVER.ShipInteractions.shipImpactDamage) return
        for (player in level.players()) playerFrames.putIfAbsent(player, playerFrame(player, level))
    }

    internal fun tickRedstone(level: ServerLevel) {
        val plates = HashMap<BlockPos, BasePressurePlateBlock>()
        val buttons = HashMap<BlockPos, ButtonBlock>()
        val visited = HashSet<Pair<Long, BlockPos>>()
        val scan = ShipSensorScan(level)
        val dimension = level.dimensionId
        val ships = level.shipObjectWorld.loadedShips
        var hasSensorShips: Boolean? = null
        for (source in ships) {
            if (source.chunkClaimDimension != dimension) continue
            val swept = ShipBlockContacts.sweptBounds(source)
            scan.scan(swept, null) { pos ->
                if (visited.add(source.id to pos)) operate(level, pos, source, null, plates, buttons)
                hasSensorShips = null
            }
            // Do not search other ships when none of them has a sensor.
            if (hasSensorShips == null) {
                hasSensorShips = ships.any { it.chunkClaimDimension == dimension && scan.hasSensors(it) }
            }
            if (hasSensorShips == false) continue
            for (target in ships.getIntersecting(swept, dimension)) {
                if (target.id == source.id) continue
                scan.scan(AABBd(swept).transform(target.worldToShip), target) { pos ->
                    if (visited.add(source.id to pos)) operate(level, pos, source, target, plates, buttons)
                    hasSensorShips = null
                }
            }
        }
        ShipButtons.update(level, buttons)
        ShipPressurePlates.update(level, plates)
    }

    private fun operate(level: ServerLevel, pos: BlockPos, source: Ship, target: Ship?,
        plates: MutableMap<BlockPos, BasePressurePlateBlock>,
        buttons: MutableMap<BlockPos, ButtonBlock>) {
        val state = level.getBlockState(pos)
        if (level.getShipManagingPos(pos)?.id != target?.id) return
        val block = state.block
        if (block is ButtonBlock) {
            if (ShipButtons.isPushed(level, pos, state, source, target)) buttons[pos] = block
            return
        }
        if (block is BasePressurePlateBlock) {
            if (ShipPressurePlates.isPushed(level, pos, source, target)) plates[pos] = block
            return
        }
        val shape = state.getShape(level, pos)
        if (shape.isEmpty) return
        val contact = ShipBlockContacts.find(level, source, shape.bounds().move(pos).toJOML(), target) ?: return
        val localVelocity = Vector3d(contact.velocity)
        target?.transform?.rotation?.transformInverse(localVelocity)
        when (block) {
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
        return ShipPressurePlates.signal(level, pos)
    }

    private fun tickPlayerDamage(level: ServerLevel) {
        val config = VSGameConfig.SERVER.ShipInteractions
        for (player in level.players()) {
            val current = playerFrame(player, level)
            val previous = playerFrames.put(player, current)?.takeIf { it.dimension == current.dimension } ?: current
            if (player.isSpectator || player.isCreative || player.isDeadOrDying) continue
            val last = damageTimes[player]
            if (last != null && level.gameTime - last in 0 until config.playerImpactCooldownTicks.coerceAtLeast(1).toLong()) continue
            var worst = 0.0
            val mounted = getShipMountedTo(player)?.id
            for (ship in level.shipObjectWorld.loadedShips) {
                if (ship.chunkClaimDimension != level.dimensionId || ship.id == previous.rider || ship.id == mounted) continue
                val swept = ShipBlockContacts.sweptBounds(ship)
                // A movement packet can place the player outside the ship after an impact.
                for (bounds in listOf(previous.bounds, current.bounds)) {
                    if (!swept.intersectsAABB(bounds)) continue
                    val contact = ShipBlockContacts.find(level, ship, bounds,
                        sensorVelocity = previous.velocity, useMovementSpeed = true) ?: continue
                    val relative = contact.velocity.sub(previous.velocity)
                    worst = max(worst, relative.dot(contact.normal))
                }
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
