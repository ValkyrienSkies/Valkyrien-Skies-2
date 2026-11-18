package org.valkyrienskies.mod.common

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.api.VsShapedBlock
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.vsCore

/**
 * Collision shapes that belong to a position rather than to a block state.
 *
 * See [VsShapedBlock] for why. This is the machinery: given a position, ask the block for its shape,
 * register it with the physics engine if it has not been seen before, and hand back the type. Shapes
 * are interned by value, so the fifty panels of a car that happen to be the same shape share one
 * registration and a panel that is beaten back to a shape already in use costs nothing.
 */
object BlockShapes {

    /**
     * The type to use for a block at a position, or null if the block has nothing special to say.
     *
     * Cheap for every block that is not [VsShapedBlock] — one `is` check — because this is on the
     * path of every block change in the world.
     */
    @JvmStatic
    fun typeAt(level: BlockGetter?, pos: BlockPos, state: BlockState): VsiBlockType? {
        val block = state.block
        if (level == null || block !is VsShapedBlock) {
            return null
        }
        val solid = block.getPhysicsShape(level, pos, state) ?: return null
        return vsCore.registerBlockStateLate(VsiBlockState(solid, null))
    }

    /**
     * How many boxes and collision points one block's shape may have.
     *
     * Not a matter of taste. Krunch's native registration reads a serialized shape into fixed
     * storage and walks off the end of it given a larger one, which is a segmentation fault on the
     * physics thread rather than an exception — so these are measured, and a shape that needs more
     * than this is refused rather than sent. [org.valkyrienskies.core.internal.collision
     * .VsiSolidShapeUtils.generateShapeFromBoxes] uses ten and twenty, which is the conservative
     * reading; these are what the engine was found to actually take.
     */
    private val MAX_BOXES = Integer.getInteger("vs.maxShapeBoxes", 10)
    private val MAX_POINTS = Integer.getInteger("vs.maxShapePoints", 20)

    /**
     * A solid state made of boxes on the sixteenth-of-a-block grid.
     *
     * The shape a voxel block already has: six numbers a box, `[x0, y0, z0, x1, y1, z1]`, with the
     * upper corner exclusive the way everything else that merges voxels into boxes reports it. Here
     * so that a block supplying its own shape does not have to reach into the physics engine's own
     * builders to say something this simple.
     *
     * The negative boxes and the collision points are not asked for and not worked out here either.
     * [org.valkyrienskies.core.internal.collision.VsiSolidShapeUtils.generateShapeFromBoxes] does
     * all three and enforces the physics engine's own limits on how many of each a shape may have —
     * ten boxes and twenty points. Those limits are real and they are not advice: Krunch's native
     * registration reads a serialized shape into fixed storage and walks off the end of it given a
     * larger one, which is a segmentation fault on the physics thread rather than an exception.
     *
     * So a shape too finely broken up to describe is refused, and null means "use the block state's
     * own". A panel that is a plate, a fold or a curve is a handful of boxes and gets its real shape;
     * one that has been crushed into a dozen separate pieces of metal goes back to being a cube.
     */
    @JvmStatic
    @JvmOverloads
    fun solidFromBoxes(
        boxes: Iterable<IntArray>,
        friction: Double = VSGameConfig.SERVER.defaultBlockFriction,
        elasticity: Double = VSGameConfig.SERVER.defaultBlockElasticity,
        hardness: Double = VSGameConfig.SERVER.defaultBlockHardness
    ): SolidState? {
        val positive = mutableListOf<AABBic>()
        for (box in boxes) {
            // Inclusive at the top, which is how a block shape is spelled here.
            positive.add(AABBi(box[0], box[1], box[2], box[3] - 1, box[4] - 1, box[5] - 1))
        }
        if (positive.isEmpty()) {
            return null
        }
        val utils = vsCore.solidShapeUtils
        val merged = utils.mergeBoxes(positive)
        if (merged.size > MAX_BOXES) {
            return null
        }
        var negative = mutableListOf<AABBic>(AABBi(0, 0, 0, 15, 15, 15))
        for (box in merged) {
            negative = utils.cutBoxes(negative, box)
        }
        utils.mergeBoxes(negative)
        if (negative.size > MAX_BOXES) {
            return null
        }
        val points = utils.generateCollisionPointsForBoxes(merged)
        if (points.size > MAX_POINTS) {
            return null
        }
        val shape = vsCore.newSolidStateBoxesShapeBuilder()
            .addPositiveBoxes(merged)
            .addNegativeBoxes(negative)
            .addCollisionPoints(points)
            .build()
        return vsCore.newSolidStateBuilder()
            .friction(friction)
            .elasticity(elasticity)
            .hardness(hardness)
            .shape(shape)
            .build()
    }

    /**
     * Tells the physics engine that the shape at this position has changed.
     *
     * For a block whose shape lives in its block entity, which can change without the block state
     * changing at all — most of the time, in fact. The mass is left alone: this says the block is a
     * different shape, not that it is a different block.
     */
    @JvmStatic
    fun onShapeChanged(level: Level, pos: BlockPos) {
        if (level !is ServerLevel) {
            return
        }
        val state = level.getBlockState(pos)
        val known = BlockStateInfo.get(state) ?: return
        val newType = typeAt(level, pos, state) ?: known.second
        // The old type is a lie, and it has to be. What is actually there is the shape this block
        // had a moment ago, which nothing records — and the update is dropped unless the two types
        // differ, so a panel beaten back to a shape the block state already describes would keep
        // whatever it was before. Air is the one type it can never already be, and the receiving
        // end reads the old type for nothing else: the mass is unchanged and whether the block is
        // air is decided by the new type.
        level.shipObjectWorld.onSetBlock(
            pos.x, pos.y, pos.z, level.dimensionId,
            vsCore.blockTypes.air, newType, known.first, known.first
        )
    }
}
