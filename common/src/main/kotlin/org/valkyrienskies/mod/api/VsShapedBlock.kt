package org.valkyrienskies.mod.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.physics.blockstates.SolidState

/**
 * A block whose collision shape is not a function of its block state.
 *
 * Valkyrien Skies normally reads a block's physics shape once per block state, at start-up, by
 * sampling it in a world that has no block entities. That is right for almost every block: its shape
 * is a function of its state, so one reading serves every copy of it for ever.
 *
 * It is wrong for a block that keeps its shape in its block entity. A voxel panel has more shapes
 * than a block state can enumerate — an eight-cubed grid has more occupancy patterns than there are
 * atoms in the universe — so the state cannot describe it, and the sampled shape is whatever the
 * block says when asked without one. In practice that means such blocks collide as solid cubes and
 * the physics engine sees a brick where the model shows a shell.
 *
 * A block implementing this is asked for its shape **per position** instead. The shape is registered
 * with the physics engine when it is first seen and shared by every block that has the same one, so
 * a car with fifty identical panels costs one registration.
 *
 * Call [org.valkyrienskies.mod.common.BlockShapes.onShapeChanged] whenever the shape changes without
 * the block state changing, which for a block entity is most of the time.
 */
interface VsShapedBlock {

    /**
     * This block's collision shape at this position, or null to use the block state's own.
     *
     * Called on the game thread, with the block entity available. Returning null is not an error and
     * is the right answer for a position that has nothing special about it.
     */
    fun getPhysicsShape(level: BlockGetter, pos: BlockPos, state: BlockState): SolidState?
}
