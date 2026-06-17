package org.valkyrienskies.mod.compat

import com.google.common.collect.ImmutableList
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl
import me.jellysquid.mods.sodium.client.gui.options.OptionPage
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl
import net.minecraft.network.chat.Component
import net.minecraftforge.common.ForgeConfigSpec
import org.valkyrienskies.core.internal.config.VsiConfigModelCategory
import org.valkyrienskies.core.internal.config.VsiConfigModelEntry
import org.valkyrienskies.mod.api.config.VSConfigApi
import org.valkyrienskies.mod.common.config.VSConfigUpdater
import org.valkyrienskies.mod.common.config.VSGameConfig

object SodiumOptionsMenu {
    // We ignore this storage, but we have to pass _something_
    val ops = SodiumDummyOptionsStorage()

    fun makeSodiumOptionPage(): OptionPage {
        val groups = mutableListOf<OptionGroup>()

        val clientConfig = VSConfigApi.buildVSConfigModel(VSGameConfig.CLIENT)

        buildSodiumConfigSpec(clientConfig.root, groups)

        return OptionPage(Component.translatable("itemGroup.valkyrienSkies"), ImmutableList.copyOf(groups))
    }

    fun buildSodiumConfigSpec(configCategory: VsiConfigModelCategory, groups: MutableList<OptionGroup>) {
        val currentBuilder = OptionGroup.createBuilder()

        for (node in configCategory.children) {
            val entry = node.value
            if (entry is VsiConfigModelCategory) {
                buildSodiumConfigSpec(entry, groups)
            } else if (entry is VsiConfigModelEntry<*>) {
                val definedNode = defineNode(entry)
                definedNode?.let {
                    currentBuilder.add(it.build())
                }
            }
        }
        groups.add(currentBuilder.build())
    }

    private fun defineNode(
        entry: VsiConfigModelEntry<*>
    ): OptionImpl.Builder<*, *>? {

        @Suppress("UNCHECKED_CAST")
        fun <T : Enum<T>> defineEnum(): OptionImpl.Builder<String, T> {
            val enumClass = entry.default!!::class.java as Class<T>

            return OptionImpl.createBuilder(enumClass, ops)
                .setName(Component.literal(entry.fancyName))
                .setTooltip(Component.literal(entry.description ?: ""))
                .setBinding(
                    { _, value -> setConfigOption(entry.name, value) },
                    { entry.getValue.invoke() as T? }
                ).setControl { option ->
                    CyclingControl(option, enumClass)
                }
        }

        @Suppress("UNCHECKED_CAST")
        fun defineBoolean(): OptionImpl.Builder<String, Boolean> {
            return OptionImpl.createBuilder(Boolean::class.java, ops)
                .setName(Component.literal(entry.fancyName))
                .setTooltip(Component.literal(entry.description ?: ""))
                .setBinding(
                    { _, value -> setConfigOption(entry.name, value) },
                    { entry.getValue.invoke() as Boolean }
                )
                .setControl(::TickBoxControl)
        }

        @Suppress("UNCHECKED_CAST")
        fun defineInt(min: Int, max: Int): OptionImpl.Builder<String, Int> {
            return OptionImpl.createBuilder(Int::class.java, ops)
                .setName(Component.literal(entry.fancyName))
                .setTooltip(Component.literal(entry.description ?: ""))
                .setBinding(
                    { _, value -> setConfigOption(entry.name, value) },
                    { entry.getValue.invoke() as Int }
                )
                .setControl { option ->
                    SliderControl(option, min, max, 1, ControlValueFormatter.number())
                }
        }

        return when (entry.getValue()) {

            is Int -> {
                val e = entry as VsiConfigModelEntry<Int>
                defineInt(e.min ?: 0, e.max ?: 10)
            }

            is Boolean -> defineBoolean() as OptionImpl.Builder<*, *>

            is Enum<*> -> defineEnum() as OptionImpl.Builder<*, *>

            else -> null
        }
    }

    fun <T> setConfigOption(key: String, value: T) {
        (VSConfigUpdater.forgeConfigValuesMap.get(key) as ForgeConfigSpec.ConfigValue<T>).set(value)
    }
}

/**
 * Might want to api-ify this at some point
 */
private val VsiConfigModelEntry<*>.fancyName: String
    get() = this.name
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }

