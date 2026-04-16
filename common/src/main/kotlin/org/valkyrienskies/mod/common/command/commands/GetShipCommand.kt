package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.BlockHitResult
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.getShipManagingPos

object GetShipCommand {
    private const val GET_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.get_ship.success"
    private const val GET_SHIP_FAIL_MESSAGE = "command.valkyrienskies.get_ship.fail"
    private const val GET_SHIP_ONLY_USABLE_BY_ENTITIES_MESSAGE = "command.valkyrienskies.get_ship.only_usable_by_entities"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("get-ship")
        .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.getShipCommandPerms)}
        .executes {
            var success = false
            val sourceEntity: Entity? = it.source.entity
            if (sourceEntity != null) {
                val rayTrace = sourceEntity.pick(10.0, 1.0.toFloat(), false)
                if (rayTrace is BlockHitResult) {
                    val ship = sourceEntity.level().getShipManagingPos(rayTrace.blockPos)
                    if (ship != null) {
                        it.source.sendSuccess(
                            {
                                translatable(GET_SHIP_SUCCESS_MESSAGE, ship.slug, ship.id)
                            }, true
                        )
                        success = true
                    }
                }
                if (success) {
                    1
                } else {
                    it.source.sendFailure(translatable(GET_SHIP_FAIL_MESSAGE))
                    0
                }
            } else {
                it.source.sendFailure(translatable(GET_SHIP_ONLY_USABLE_BY_ENTITIES_MESSAGE))
                0
            }

        })
    }
}
