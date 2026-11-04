package org.valkyrienskies.mod.common.fluid

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.AbstractCandleBlock
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.LiquidBlockContainer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import org.valkyrienskies.core.internal.physics.VsiFluidAbsorption
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidGameplaySnapshot
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import java.util.UUID
import java.util.function.IntPredicate

class RetainedFluidBlockInteractions {
    private enum class Solidification { LAVA, WATER }

    private data class Target(val dimension: String, val bodyId: Long, val x: Int, val y: Int, val z: Int,
        val farmland: Boolean = false, val reaction: Boolean = false, val solidification: Solidification? = null) {
        fun position() = BlockPos(x, y, z)
        fun matches(state: BlockState) = when {
            solidification != null -> state.isAir
            farmland -> isDryFarmland(state)
            reaction -> RetainedWaterReactions.acceptsWater(state)
            else -> state.`is`(Blocks.SPONGE)
        }
    }
    private class Scan(var cursor: Int = 0, var startedAt: Long = 0, var unloaded: Boolean = false)

    private val pending = LinkedHashMap<UUID, Target>()
    private val targets = HashMap<Target, UUID>()
    private val dirtyBodies = LinkedHashMap<Long, Scan>()
    private val invalidated = HashSet<UUID>()
    private val requestSequences = HashMap<UUID, Long>()
    private var admissions = 0

    fun onSnapshot(snapshot: VsiFluidGameplaySnapshot) {
        if (usable(snapshot) && snapshot.flooding.voxels.isNotEmpty()) dirtyBodies.putIfAbsent(snapshot.bodyId, Scan())
        else dirtyBodies.remove(snapshot.bodyId)
    }

    fun tick(world: VsiServerShipWorld, levels: Map<String, ServerLevel>) {
        admissions = 0
        complete(world, levels)
        var remaining = MAX_CELLS_PER_TICK
        val selected = dirtyBodies.keys.take(MAX_BODIES_PER_TICK)
        for (bodyId in selected) {
            if (remaining == 0) break
            val scan = dirtyBodies.remove(bodyId) ?: continue
            val snapshot = world.getFluidGameplaySnapshot(bodyId)
            if (snapshot == null || !usable(snapshot)) continue
            val level = levels[snapshot.dimensionId] ?: continue
            if (scan.cursor == 0) {
                scan.startedAt = snapshot.streamSequence
                scan.unloaded = false
            }
            val voxels = snapshot.flooding.voxels
            val body by lazy { ShipFluidInteraction.bodyFor(world, bodyId) }
            val end = minOf(voxels.size, scan.cursor + remaining)
            while (scan.cursor < end) {
                val voxel = voxels[scan.cursor++]
                --remaining
                val pos = BlockPos(voxel.positionX, voxel.positionY, voxel.positionZ)
                if (!level.hasChunkAt(pos)) { scan.unloaded = true; continue }
                if (level.getShipManagingPos(pos)?.id != bodyId) continue
                if (world.getFluidGameplaySnapshot(bodyId) !== snapshot) break
                val state = level.getBlockState(pos)
                if (RetainedFluidContact.volume(voxel, LAVA) > 0 &&
                    !reactLava(level, world, snapshot, body, pos, voxel)) scan.unloaded = true
                if (!react(level, world, snapshot, pos, voxel, pos, state)) scan.unloaded = true
                if (isWaterBreakable(state) && RetainedFluidContact.canDestroy(voxel,
                        state.getShape(level, pos).toAabbs(), WATER)) {
                    level.destroyBlock(pos, true)
                }
                for (direction in Direction.values()) {
                    val neighbor = pos.relative(direction)
                    if (!level.hasChunkAt(neighbor)) continue
                    val adjacent = level.getBlockState(neighbor)
                    if (adjacent.block is LiquidBlock && adjacent.fluidState.`is`(FluidTags.LAVA) &&
                        level.getShipManagingPos(neighbor)?.id == bodyId &&
                        RetainedFluidContact.touches(voxel, liquidBounds(pos, neighbor, adjacent), WATER)) {
                        if (!solidifyBlockLiquid(level, snapshot.dimensionId, neighbor, adjacent)) scan.unloaded = true
                    } else if (adjacent.`is`(Blocks.SPONGE)) {
                        if (!queueSponge(level, neighbor, world)) scan.unloaded = true
                    } else if (!react(level, world, snapshot, pos, voxel, neighbor, adjacent)) {
                        scan.unloaded = true
                    }
                }
            }
            if (scan.cursor >= voxels.size) {
                scan.cursor = 0
                if (snapshot.streamSequence == scan.startedAt && !scan.unloaded) continue
            }
            dirtyBodies[bodyId] = scan
        }
    }

    private fun complete(world: VsiServerShipWorld, levels: Map<String, ServerLevel>) {
        for (id in pending.keys.take(MAX_REQUESTS_PER_TICK)) {
            val target = pending[id] ?: continue
            pending.remove(id)
            pending[id] = target
            val level = levels[target.dimension]
            if (level != null && !level.hasChunkAt(target.position())) continue
            val pos = target.position()
            val currentPlacement = level != null && target.matches(level.getBlockState(pos)) &&
                level.getShipManagingPos(pos)?.id == target.bodyId
            val present = currentPlacement && id !in invalidated
            if ((!present && world.cancelFluidAbsorption(id)) || !world.hasFluidAbsorption(id)) {
                if (!target.farmland && currentPlacement) dirtyBodies.putIfAbsent(target.bodyId, Scan())
                forget(id, target)
                continue
            }
            if (world.consumeFluidAbsorption(id) { result ->
                    if (!target.farmland && present && result.acceptedAmount == 0.0 &&
                        world.getFluidGameplaySnapshot(target.bodyId)?.streamSequence != requestSequences[id])
                        dirtyBodies.putIfAbsent(target.bodyId, Scan())
                    if (present && result.acceptedAmount > 0) {
                        val state = level!!.getBlockState(pos)
                        if (target.matches(state)) {
                            if (target.solidification != null) {
                                val product = when {
                                    target.solidification == Solidification.WATER -> Blocks.STONE
                                    result.acceptedAmount >= 255.0 -> Blocks.OBSIDIAN
                                    else -> Blocks.COBBLESTONE
                                }
                                check(level.setBlock(pos, product.defaultBlockState(), Block.UPDATE_ALL)) {
                                    "Could not complete retained-fluid solidification"
                                }
                                level.levelEvent(1501, pos, 0)
                            } else if (target.reaction) {
                                check(RetainedWaterReactions.apply(level, pos, state)) { "Could not complete retained-water reaction" }
                            } else {
                                val wet = if (target.farmland) state.setValue(FarmBlock.MOISTURE, 7)
                                    else Blocks.WET_SPONGE.defaultBlockState()
                                check(level.setBlock(pos, wet, 2)) { "Could not complete retained-water absorption" }
                                if (!target.farmland)
                                    level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(Blocks.WATER.defaultBlockState()))
                            }
                        }
                    }
                }) {
                if (!target.farmland && currentPlacement && !present) dirtyBodies.putIfAbsent(target.bodyId, Scan())
                forget(id, target)
            }
        }
    }

    private fun forget(id: UUID, target: Target) {
        pending.remove(id)
        targets.remove(target)
        invalidated.remove(id)
        requestSequences.remove(id)
    }

    private fun reactLava(level: ServerLevel, world: VsiServerShipWorld, snapshot: VsiFluidGameplaySnapshot,
        body: ShipFluidInteraction.CachedBody?, pos: BlockPos, lava: VsiFluidFloodedVoxel): Boolean {
        if (!world.supportsFluidAbsorption || body == null) return true
        val up = lava.surfaceRegions.firstOrNull()
        val down = if (up == null) Direction.DOWN else Direction.getNearest(-up.normalX, -up.normalY, -up.normalZ)
        val below = pos.relative(down)
        if (level.hasChunkAt(below) && level.getShipManagingPos(below)?.id == snapshot.bodyId) {
            val state = level.getBlockState(below)
            if (state.block is LiquidBlock && state.fluidState.`is`(FluidTags.WATER) &&
                RetainedFluidContact.touches(lava, liquidBounds(pos, below, state), LAVA)) {
                return solidifyBlockLiquid(level, snapshot.dimensionId, below, state, Blocks.STONE)
            }
        }
        val lowerWater = body.getFloodedVoxel(below.x, below.y, below.z)
        if (lowerWater != null && RetainedFluidContact.volume(lowerWater, WATER) >= RetainedFluidContact.DESTRUCTION_VOLUME &&
            RetainedFluidContact.materialsTouch(lava, LAVA, lowerWater, WATER) &&
            level.hasChunkAt(below) && level.getBlockState(below).isAir) {
            return queueSolidification(world, snapshot, below, Solidification.WATER)
        }
        if (RetainedFluidContact.volume(lava, LAVA) < RetainedFluidContact.DESTRUCTION_VOLUME ||
            !level.getBlockState(pos).isAir) return true
        if (RetainedFluidContact.materialsTouch(lava, LAVA, lava, WATER))
            return queueSolidification(world, snapshot, pos, Solidification.LAVA)
        for (direction in Direction.values()) {
            if (direction == down) continue
            val neighbor = pos.relative(direction)
            if (level.hasChunkAt(neighbor) && level.getShipManagingPos(neighbor)?.id == snapshot.bodyId) {
                val state = level.getBlockState(neighbor)
                if (state.block is LiquidBlock && state.fluidState.`is`(FluidTags.WATER) &&
                    RetainedFluidContact.touches(lava, liquidBounds(pos, neighbor, state), LAVA))
                    return queueSolidification(world, snapshot, pos, Solidification.LAVA)
            }
            val water = body.getFloodedVoxel(neighbor.x, neighbor.y, neighbor.z) ?: continue
            if (RetainedFluidContact.materialsTouch(lava, LAVA, water, WATER))
                return queueSolidification(world, snapshot, pos, Solidification.LAVA)
        }
        return true
    }

    private fun liquidBounds(origin: BlockPos, pos: BlockPos, state: BlockState): AABB {
        val x = (pos.x - origin.x).toDouble()
        val y = (pos.y - origin.y).toDouble()
        val z = (pos.z - origin.z).toDouble()
        return AABB(x, y, z, x + 1.0, y + state.fluidState.amount / 8.0, z + 1.0)
    }

    private fun solidifyBlockLiquid(level: ServerLevel, dimension: String, pos: BlockPos, state: BlockState,
        product: Block? = null): Boolean {
        val sources = FractionalFluidSources.forLevel(level)
        val key = FractionalFluidSources.Position(dimension, pos.x, pos.y, pos.z)
        if (sources?.transfers?.isLocked(key) == true) return false
        val name = requireNotNull(FractionalFluidSources.fluidName(state))
        val exactUnits = sources?.amount(dimension, pos.x, pos.y, pos.z, name)
        val units = exactUnits
            ?: (state.fluidState.amount * (255.0 / 8.0)).toBigDecimal()
        if (units < (255.0 / 8.0).toBigDecimal()) return true
        val full = exactUnits?.let { it >= FractionalFluidSources.FULL_SOURCE } ?: state.fluidState.isSource
        val solid = product ?: if (full) Blocks.OBSIDIAN else Blocks.COBBLESTONE
        if (!level.setBlock(pos, solid.defaultBlockState(), Block.UPDATE_ALL)) return false
        sources?.reconcileFluid(dimension, pos.x, pos.y, pos.z, null)
        level.levelEvent(1501, pos, 0)
        return true
    }

    private fun queueSolidification(world: VsiServerShipWorld, snapshot: VsiFluidGameplaySnapshot,
        pos: BlockPos, solidification: Solidification): Boolean {
        val target = Target(snapshot.dimensionId, snapshot.bodyId, pos.x, pos.y, pos.z, solidification = solidification)
        if (Solidification.entries.any { targets.containsKey(target.copy(solidification = it)) }) return true
        if (pending.size >= MAX_PENDING || admissions >= MAX_REQUESTS_PER_TICK) return false
        return submit(world, target, snapshot, pos)
    }

    private fun react(level: ServerLevel, world: VsiServerShipWorld, snapshot: VsiFluidGameplaySnapshot,
        waterPos: BlockPos, voxel: VsiFluidFloodedVoxel, pos: BlockPos, state: BlockState): Boolean {
        if (!RetainedWaterReactions.acceptsWater(state) ||
            level.getShipManagingPos(pos)?.id != snapshot.bodyId) return true
        val target = Target(snapshot.dimensionId, snapshot.bodyId, pos.x, pos.y, pos.z, reaction = true)
        if (targets.containsKey(target)) return true
        val volume = RetainedFluidContact.volume(voxel, WATER)
        if (volume <= 0 || !RetainedWaterReactions.touches(level, pos, state, waterPos, voxel, WATER)) return true
        if (volume >= RetainedFluidContact.DESTRUCTION_VOLUME) return RetainedWaterReactions.apply(level, pos, state)
        if (!world.supportsFluidAbsorption || voxel.pressureAmount != 0) return true
        if (pending.size >= MAX_PENDING || admissions >= MAX_REQUESTS_PER_TICK) return false
        return submit(world, target, snapshot, waterPos, Math.nextDown(255.0 / 8))
    }

    private fun queueSponge(level: ServerLevel, pos: BlockPos, world: VsiServerShipWorld): Boolean {
        if (!world.supportsFluidAbsorption || !level.hasChunkAt(pos) ||
            !level.getBlockState(pos).`is`(Blocks.SPONGE)) return true
        val bodyId = level.getShipManagingPos(pos)?.id ?: return true
        val target = Target(level.dimensionId, bodyId, pos.x, pos.y, pos.z)
        if (targets.containsKey(target)) return true
        if (pending.size >= MAX_PENDING || admissions >= MAX_REQUESTS_PER_TICK) return false
        val snapshot = world.getFluidGameplaySnapshot(bodyId) ?: return true
        if (!usable(snapshot) || snapshot.dimensionId != target.dimension) return true
        return submit(world, target, snapshot, pos)
    }

    private fun queueFarmland(level: ServerLevel, pos: BlockPos, world: VsiServerShipWorld): Boolean {
        if (!world.supportsFluidAbsorption || !isDryFarmland(level.getBlockState(pos))) return false
        val bodyId = level.getShipManagingPos(pos)?.id ?: return false
        val target = Target(level.dimensionId, bodyId, pos.x, pos.y, pos.z, farmland = true)
        if (targets.containsKey(target)) return true
        if (level.isRainingAt(pos.above())) return false
        val snapshot = world.getFluidGameplaySnapshot(bodyId) ?: return false
        if (!usable(snapshot) || snapshot.dimensionId != target.dimension) return false
        val body = ShipFluidInteraction.bodyFor(world, bodyId) ?: return false
        val above = pos.above()
        for (cell in listOf(pos, above)) {
            val voxel = body.getFloodedVoxel(cell.x, cell.y, cell.z) ?: continue
            val volume = RetainedFluidContact.volume(voxel, WATER)
            if (volume <= 0 || volume >= RetainedFluidContact.DESTRUCTION_VOLUME || voxel.pressureAmount != 0) continue
            val touches = if (cell == pos) level.getBlockState(pos).getShape(level, pos).toAabbs()
                .any { RetainedFluidContact.touches(voxel, it, WATER) }
                else RetainedFluidContact.touches(voxel, CELL_FLOOR, WATER)
            if (!touches) continue
            if (pending.size < MAX_PENDING && admissions < MAX_REQUESTS_PER_TICK)
                submit(world, target, snapshot, cell, Math.nextDown(255.0 / 8))
            return true
        }
        return false
    }

    private fun submit(world: VsiServerShipWorld, target: Target, snapshot: VsiFluidGameplaySnapshot,
        cell: BlockPos, shallowAmount: Double? = null): Boolean {
        val material = if (target.solidification == Solidification.LAVA) LAVA else WATER
        val ids = MassDatapackResolver.fluidTypesForClient().keys.asSequence().filter { material.test(it) }.take(4097).toList()
        if (ids.isEmpty() || ids.size > 4096) return true
        if (world.getFluidGameplaySnapshot(target.bodyId) !== snapshot) return false
        val id = UUID.randomUUID()
        val request = VsiFluidAbsorption(id, target.dimension, target.bodyId, snapshot.topology.sourceRevision,
            snapshot.topology.nativePublicationId, cell.x, cell.y, cell.z, ids,
            maximumDepth = if (shallowAmount == null && target.solidification == null) 6 else 0,
            maximumCells = if (shallowAmount == null && target.solidification == null) 64 else 1,
            maximumAmount = if (target.solidification != null) 255.0 else shallowAmount ?: 64.0 * 255,
            shallowCell = shallowAmount != null, solidifyCell = target.solidification != null)
        pending[id] = target
        targets[target] = id
        requestSequences[id] = snapshot.streamSequence
        ++admissions
        if (!world.submitFluidAbsorption(request)) {
            forget(id, target)
            return false
        }
        return true
    }

    fun save(): CompoundTag = CompoundTag().also { tag ->
        tag.putInt("version", 1)
        tag.put("pending", ListTag().also { rows ->
            for ((id, target) in pending) rows.add(CompoundTag().also {
                it.putUUID("id", id)
                it.putString("dimension", target.dimension)
                it.putLong("body", target.bodyId)
                it.putInt("x", target.x); it.putInt("y", target.y); it.putInt("z", target.z)
                if (id in invalidated) it.putBoolean("invalidated", true)
                if (target.farmland) it.putBoolean("farmland", true)
                if (target.reaction) it.putBoolean("reaction", true)
                target.solidification?.let { value -> it.putString("solidification", value.name) }
                it.putLong("sequence", requestSequences.getValue(id))
            })
        })
    }

    fun load(tag: CompoundTag) {
        require(tag.getInt("version") == 1) { "Unsupported retained-fluid block interaction version" }
        val rows = tag.getList("pending", Tag.TAG_COMPOUND.toInt())
        require(rows.size <= MAX_PENDING)
        val restored = LinkedHashMap<UUID, Target>()
        val restoredTargets = HashMap<Target, UUID>()
        val restoredInvalidated = HashSet<UUID>()
        val restoredSequences = HashMap<UUID, Long>()
        for (index in 0 until rows.size) {
            val row = rows.getCompound(index)
            val id = row.getUUID("id")
            val target = Target(row.getString("dimension"), row.getLong("body"), row.getInt("x"), row.getInt("y"), row.getInt("z"),
                row.getBoolean("farmland"), row.getBoolean("reaction"),
                if (row.contains("solidification")) Solidification.valueOf(row.getString("solidification")) else null)
            require(target.solidification == null || !target.farmland && !target.reaction)
            require(!target.farmland || !target.reaction)
            require(target.dimension.isNotEmpty() && target.dimension.length <= 1024)
            require(restored.put(id, target) == null && restoredTargets.put(target, id) == null)
            if (row.getBoolean("invalidated")) restoredInvalidated.add(id)
            val sequence = row.getLong("sequence")
            require(sequence >= 0)
            restoredSequences[id] = sequence
        }
        pending.clear(); pending.putAll(restored)
        targets.clear(); targets.putAll(restoredTargets)
        invalidated.clear(); invalidated.addAll(restoredInvalidated)
        requestSequences.clear(); requestSequences.putAll(restoredSequences)
        dirtyBodies.clear()
    }

    companion object {
        private const val MAX_PENDING = 4096
        private const val MAX_CELLS_PER_TICK = 256
        private const val MAX_BODIES_PER_TICK = 64
        private const val MAX_REQUESTS_PER_TICK = 64
        private val CELL_FLOOR = AABB(0.0, 0.0, 0.0, 1.0, 0.0, 1.0)
        private val LAVA = IntPredicate { id -> MassDatapackResolver.getFlowingFluid(id)?.defaultFluidState()?.`is`(FluidTags.LAVA) == true }
        private val WATER = IntPredicate { id -> MassDatapackResolver.getFlowingFluid(id)?.defaultFluidState()?.`is`(FluidTags.WATER) == true }

        private fun usable(snapshot: VsiFluidGameplaySnapshot) = snapshot.floodingIncluded &&
            snapshot.topology.enabled && snapshot.topology.valid

        private fun isDryFarmland(state: BlockState) = state.`is`(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) == 0

        @JvmStatic fun requestFarmland(level: ServerLevel, pos: BlockPos): Boolean {
            val provider = level.server as? IShipObjectWorldServerProvider ?: return false
            val world = provider.shipObjectWorld ?: return false
            return provider.fluidBlockInteractions?.queueFarmland(level, pos, world) == true
        }

        @JvmStatic fun requestSponge(level: Level, pos: BlockPos) {
            if (level !is ServerLevel) return
            val provider = level.server as? IShipObjectWorldServerProvider ?: return
            val world = provider.shipObjectWorld ?: return
            provider.fluidBlockInteractions?.queueSponge(level, pos, world)
        }

        @JvmStatic fun onBlockChanged(level: Level, pos: BlockPos, previous: BlockState, current: BlockState) {
            if (level !is ServerLevel) return
            val farmland = isDryFarmland(previous) && !isDryFarmland(current)
            val wasReaction = RetainedWaterReactions.acceptsWater(previous)
            val isReaction = RetainedWaterReactions.acceptsWater(current)
            val blockChanged = previous.block !== current.block
            val reaction = wasReaction && (!isReaction || blockChanged)
            val newReaction = isReaction && (!wasReaction || blockChanged)
            val sponge = previous.`is`(Blocks.SPONGE) && !current.`is`(Blocks.SPONGE)
            val solidifiedCellChanged = previous.isAir && !current.isAir
            if (!farmland && !reaction && !newReaction && !sponge && !solidifiedCellChanged) return
            val provider = level.server as? IShipObjectWorldServerProvider ?: return
            val manager = provider.fluidBlockInteractions ?: return
            val bodyId = level.getShipManagingPos(pos)?.id ?: return
            if (solidifiedCellChanged) for (kind in Solidification.entries) {
                manager.targets[Target(level.dimensionId, bodyId, pos.x, pos.y, pos.z, solidification = kind)]
                    ?.let { manager.invalidated.add(it) }
            }
            if (farmland || reaction || sponge)
                manager.targets[Target(level.dimensionId, bodyId, pos.x, pos.y, pos.z, farmland, reaction)]
                    ?.let { manager.invalidated.add(it) }
            if (newReaction && provider.shipObjectWorld?.getFluidGameplaySnapshot(bodyId)?.flooding?.voxels?.isNotEmpty() == true)
                manager.dirtyBodies.putIfAbsent(bodyId, Scan())
        }

        @JvmStatic fun isWaterBreakable(state: BlockState): Boolean {
            val block = state.block
            return !state.isAir && !state.blocksMotion() && block !is LiquidBlock && block !is LiquidBlockContainer &&
                block !is DoorBlock && block !is AbstractCandleBlock && block !is CampfireBlock &&
                !state.`is`(BlockTags.SIGNS) && !state.`is`(Blocks.LADDER) &&
                !state.`is`(Blocks.SUGAR_CANE) && !state.`is`(Blocks.BUBBLE_COLUMN) &&
                !state.`is`(Blocks.NETHER_PORTAL) && !state.`is`(Blocks.END_PORTAL) &&
                !state.`is`(Blocks.END_GATEWAY) && !state.`is`(Blocks.STRUCTURE_VOID)
        }
    }
}
