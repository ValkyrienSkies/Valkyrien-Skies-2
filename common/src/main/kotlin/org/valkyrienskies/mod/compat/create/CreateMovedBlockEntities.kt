package org.valkyrienskies.mod.compat.create

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity
import com.simibubi.create.content.kinetics.base.IRotate
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** Only call when Create is loaded. */
internal object CreateMovedBlockEntities {

    fun detachKinetics(entity: BlockEntity) {
        if (entity !is KineticBlockEntity || entity.level?.isClientSide != false) return
        entity.setNetwork(null)
        entity.detachKinetics()
    }

    fun loadDeferred(entity: BlockEntity, tag: CompoundTag) {
        if (entity !is KineticBlockEntity) {
            entity.load(tag)
            return
        }
        detachKinetics(entity)
        entity.warnOfMovement()
        entity.sequenceContext = null
        entity.load(tag)
        entity.updateSpeed = true
        if (entity is GeneratingKineticBlockEntity && entity.isSource) entity.reActivateSource = true
    }

    fun relocate(state: BlockState, tag: CompoundTag, moved: ((BlockPos) -> BlockPos)?) {
        if (state.block !is IRotate) return

        tag.remove("Speed")
        tag.remove("Source")
        tag.remove("Network")
        tag.remove("Sequence")
        if (state.block is WaterWheelBlock || state.block is LargeWaterWheelBlock) tag.remove("FlowScore")

        // Belt segments point at their controller by absolute position.
        if (moved != null && state.block is BeltBlock && tag.contains("Controller", Tag.TAG_COMPOUND.toInt())) {
            val controller = NbtUtils.readBlockPos(tag.getCompound("Controller"))
            tag.put("Controller", NbtUtils.writeBlockPos(moved(controller)))
        }
    }
}
