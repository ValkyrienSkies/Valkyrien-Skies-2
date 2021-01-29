package net.examplemod.item

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState

class ShipCreatorItem(properties: Properties) : Item(properties) {

    override fun useOn(useOnContext: UseOnContext?): InteractionResult {
        val player = useOnContext!!.player
        val level = useOnContext.level
        val blockPos = useOnContext.clickedPos
        val blockState: BlockState = level.getBlockState(blockPos)

        println("Player right clicked on $blockPos")

        return super.useOn(useOnContext)
    }
}