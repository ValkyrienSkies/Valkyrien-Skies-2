package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable
import net.minecraftforge.common.ForgeConfigSpec
import org.valkyrienskies.core.impl.api_impl.config.ConfigPhysicsBackendType
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.mod.common.config.VSConfigUpdater
import org.valkyrienskies.mod.common.config.VSGameConfig

object BackendCommand {
    private const val LOD_CURRENT_MESSAGE = "command.valkyrienskies.lod.current"
    private const val LOD_SET_MESSAGE = "command.valkyrienskies.lod.set"
    private const val BACKEND_CURRENT_MESSAGE = "command.valkyrienskies.backend.current"
    private const val BACKEND_SET_MESSAGE = "command.valkyrienskies.backend.set"
    private const val LOD_DISABLED_MESSAGE = "command.valkyrienskies.lod.disabled"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("backend")
            .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.changeBackendCommandPerms)}
            .then(literal("engine")
                .then(literal("krunch")
                    .executes {
                        VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_CLASSIC
                        (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_CLASSIC.name)

                        it.source.sendSuccess(
                            {
                                translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                            }, true
                        )

                        1
                    }
                ).then(literal("DEFAULT")
                    .executes {
                        VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_CLASSIC
                        (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_CLASSIC.name)

                        it.source.sendSuccess(
                            {
                                translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                            }, true
                        )

                        1
                    }
                ).then(literal("physx")
                    .executes {
                        VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_PHYSX
                        (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_PHYSX.name)

                        it.source.sendSuccess(
                            {
                                translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                            }, true
                        )

                        1
                    }
                ).then(literal("jolt")
                    .executes {
                        VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_JOLT
                        (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_JOLT.name)

                        it.source.sendSuccess(
                            {
                                translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                            }, true
                        )

                        1
                    }
                ).executes {
                    it.source.sendSuccess(
                        {
                            translatable(BACKEND_CURRENT_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                        }, true
                    )

                    1
                }
            )
            .then(literal("lodDetail")
                .then(argument("amount", IntegerArgumentType.integer(-1))
                    .executes {
                        var amount = IntegerArgumentType.getInteger(it, "amount")
                        VSCoreConfig.SERVER.physics.lodDetail = amount
                        (VSConfigUpdater.forgeConfigValuesMap.get("lodDetail") as ForgeConfigSpec.ConfigValue<Int>).set(amount)

                        val msg = if (amount == -1) {
                            Component.translatable(LOD_DISABLED_MESSAGE)
                        } else {
                            amount.toString()
                        }

                        it.source.sendSuccess(
                            {
                                translatable(LOD_SET_MESSAGE, msg)
                            }, true
                        )

                        1
                    }
                )
                .executes {
                    val lod = VSCoreConfig.SERVER.physics.lodDetail
                    val msg = if (lod == -1) {
                        Component.translatable(LOD_DISABLED_MESSAGE)
                    } else {
                        lod.toString()
                    }

                    it.source.sendSuccess(
                        {
                            translatable(LOD_CURRENT_MESSAGE, msg)
                        },
                        true
                    )

                    1
                }
            )
        )
    }
}
