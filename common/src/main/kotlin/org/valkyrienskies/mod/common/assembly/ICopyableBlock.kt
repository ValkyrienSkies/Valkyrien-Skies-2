package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.jetbrains.annotations.ApiStatus
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ServerShip
import java.util.function.Supplier

//TODO refine docs
//TODO finish
//TODO move to .api?
/**
 * Should be inherited by block, not block entity
 */
@ApiStatus.Experimental
interface ICopyableBlock {
    /**
     * Should be called on copy of the blocks.
     *
     * If copied from ground, use -1 as ShipID.
     *
     * @return If returns tag, then copy fn should save that tag. If returns null, then copy fn should get tag from block entity if it exists.
     */
    fun onCopy(level: ServerLevel, pos: BlockPos, state: BlockState, be: BlockEntity?, shipsBeingCopied: List<ServerShip>, centerPositions: Map<Long, Vector3dc>): CompoundTag?

    /**
     * If a block under [pos] is a block entity, or it returned a tag during [onCopy], [onPaste] will be called with that tag.
     *
     * If a block is block entity, [onCopy] will be called before fn to load block entity, and the tag used will be either tag that's returned from [onPaste], or [tag] argument.
     *
     * Should be called for all ICopyableBlock's after all ships were created and all blocks were placed.
     *
     * If copied from ground, use -1 as ShipID.
     *
     * If ShipID is -1 then it's copied to ground.
     *
     * [centerPositions] should use old shipId's as keys, with values being a pair of old center, and new center.
     */
    fun onPaste(level: ServerLevel, pos: BlockPos, state: BlockState, oldShipIdToNewId: Map<Long, Long>, centerPositions: Map<Long, Pair<Vector3dc, Vector3dc>>, tag: CompoundTag?): CompoundTag?

    /**
     * Should return what items are required for pasting in survival. Should be used if cross-ship connection requires additional items. Should be ignored for creative schematics.
     */
    fun pasteSurvivalCost(data: Supplier<CompoundTag>?): Map<Item, Int>? = null
}
