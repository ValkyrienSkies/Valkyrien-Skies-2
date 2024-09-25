package org.valkyrienskies.mod.util

import org.valkyrienskies.mod.common.config.VSGameConfig
import kotlin.math.floor

object Accessibility {
    fun massToImperial(mass: Double): Pair<Int, Int> {
        val ounces = mass * 35.274
        val pounds = floor(ounces / 16)

        return pounds.toInt() to floor((ounces / 16 - pounds) * 16).toInt()
    }

    fun massToImperialStr(mass: Double): String {
        val imperial = massToImperial(mass)

        val res = StringBuilder()
        if (imperial.first > 0) {
            res.append(' ')
            res.append(imperial.first)
            res.append(if (imperial.first == 1) "lb" else "lbs")
        }

        if (imperial.second > 0) {
            res.append(' ')
            res.append(imperial.second)
            res.append("oz")
        }

        return res.toString()
    }

    val useImperial
        get() = VSGameConfig.CLIENT.useImperialUnits

    fun massToStr(mass: Double): String {
        val rounded = mass * 100 / 100
        return if (useImperial)
            massToImperialStr(rounded)
        else "${rounded}kg"
    }
}
