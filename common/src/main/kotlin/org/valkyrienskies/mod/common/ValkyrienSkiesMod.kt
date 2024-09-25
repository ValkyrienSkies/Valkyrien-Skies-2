package org.valkyrienskies.mod.common

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import org.valkyrienskies.core.api.ships.setAttachment
import org.valkyrienskies.core.apigame.VSCore
import org.valkyrienskies.core.apigame.VSCoreClient
import org.valkyrienskies.core.impl.hooks.VSEvents
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.networking.VSGamePackets
import org.valkyrienskies.mod.common.util.GameTickForceApplier
import org.valkyrienskies.mod.util.Accessibility

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    lateinit var TEST_CHAIR: Block
    lateinit var TEST_HINGE: Block
    lateinit var TEST_FLAP: Block
    lateinit var TEST_WING: Block
    lateinit var TEST_SPHERE: Block
    lateinit var SHIP_CREATOR_ITEM: Item
    lateinit var SHIP_ASSEMBLER_ITEM: Item
    lateinit var SHIP_CREATOR_ITEM_SMALLER: Item
    lateinit var PHYSICS_ENTITY_CREATOR_ITEM: Item
    lateinit var SHIP_MOUNTING_ENTITY_TYPE: EntityType<ShipMountingEntity>
    lateinit var PHYSICS_ENTITY_TYPE: EntityType<VSPhysicsEntity>
    lateinit var TEST_HINGE_BLOCK_ENTITY_TYPE: BlockEntityType<TestHingeBlockEntity>

    @JvmStatic
    var currentServer: MinecraftServer? = null

    @JvmStatic
    lateinit var vsCore: VSCore

    @JvmStatic
    val vsCoreClient get() = vsCore as VSCoreClient

    fun init(core: VSCore) {
        this.vsCore = core

        BlockStateInfo.init()
        VSGamePackets.register()
        VSGamePackets.registerHandlers()

        core.registerConfigLegacy("vs", VSGameConfig::class.java)
        VSEvents.ShipLoadEvent.on { event ->
            event.ship.setAttachment(GameTickForceApplier())
        }

        VSGameEvents.itemTooltips.on { (itemStack, _, list, flag) ->
            if (!VSGameConfig.CLIENT.Tooltip.massTooltipVisibility.isVisible(flag)) return@on

            val item = itemStack.item
            if (item !is BlockItem) return@on

            val (mass) = BlockStateInfo.get(item.block.defaultBlockState()) ?: return@on

            list.add(TranslatableComponent("tooltip.valkyrienskies.mass")
                    .append(": " + Accessibility.massToStr(mass))
                    .withStyle(ChatFormatting.DARK_GRAY))
        }
    }
}
