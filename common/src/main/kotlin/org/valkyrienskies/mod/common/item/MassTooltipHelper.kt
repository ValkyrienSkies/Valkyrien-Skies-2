package org.valkyrienskies.mod.common.item

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component


fun makeMassComponent(mass: Double, density: Boolean, imperial: Boolean, color: Boolean): Component {
    val first: Component =
        Component.translatable("tooltip.valkyrienskies." + (if (density) "density" else "mass")).append(": ")
            .withStyle(ChatFormatting.DARK_GRAY)

    val second: Component = Component.literal(
        (if (imperial) String.format("%.2f", toImperial(mass)) + "lb" else String.format("%.2f", mass) + "kg") + (if (density) "/B" else "")
    ).withStyle(
        if (color) getColorForMass(mass) else ChatFormatting.DARK_GRAY
    )
    return Component.empty().append(first).append(second)
}

fun toImperial(massKg: Double) = massKg * 2.20462262185
fun toMetric(massLb: Double) = massLb / 2.20462262185

fun getColorForMass(massKg: Double): ChatFormatting {
    return when (massKg) {
        404.0 -> ChatFormatting.DARK_RED
        in 0.0..<50.0 -> ChatFormatting.GRAY
        in 50.0..<600.0 -> ChatFormatting.AQUA
        in 600.0..<2500.0 -> ChatFormatting.GREEN
        in 2500.0..<6000.0 -> ChatFormatting.YELLOW
        else -> ChatFormatting.RED
    }
}
