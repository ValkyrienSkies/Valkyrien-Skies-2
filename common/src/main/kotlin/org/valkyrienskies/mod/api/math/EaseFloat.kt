package org.valkyrienskies.mod.api.math

import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_IN_EXPO
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_IN_OUT_EXPO
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_IN_OUT_QUAD
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_IN_OUT_SINE
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_IN_QUAD
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_IN_SINE
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_OUT_EXPO
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_OUT_QUAD
import org.valkyrienskies.mod.api.math.EaseFloat.EaseType.EASE_OUT_SINE

class EaseFloat {
    private lateinit var type: EaseType
    private var speed: Float = 0.0f
    private var target: Float = 0.0f
    private var current: Float = 0.0f
    private var start: Float = 0.0f
    private var eased: Float = 0.0f
    private var done: Boolean = false

    fun EaseFloat(speed: Float, type: EaseType) {
        this.type = type
        this.speed = speed
    }

    fun tick() {
        var mult: Float
        if (start < target && current < target) {
            mult = (target - start)

        } else if (start > target && current > target) {
            mult = (start - target)

        } else {
            done = true
            return
        }
        done = false
        val percent = current / mult

        current += speed

        when (type) {
            EASE_IN_SINE -> eased = EaseHelper.easeInSine(percent) * mult
            EASE_OUT_SINE -> eased = EaseHelper.easeOutSine(percent) * mult
            EASE_IN_OUT_SINE -> eased = EaseHelper.easeInOutSine(percent) * mult
            EASE_IN_QUAD -> eased = EaseHelper.easeInQuad(percent) * mult
            EASE_OUT_QUAD -> eased = EaseHelper.easeOutQuad(percent) * mult
            EASE_IN_OUT_QUAD -> eased = EaseHelper.easeInOutQuad(percent) * mult
            EASE_IN_EXPO -> eased = EaseHelper.easeInExpo(percent) * mult
            EASE_OUT_EXPO -> eased = EaseHelper.easeOutExpo(percent) * mult
            EASE_IN_OUT_EXPO -> eased = EaseHelper.easeInOutExpo(percent) * mult
            else -> throw RuntimeException()
        }

    }

    fun setTarget(target: Float) {
        done = false

        this.target = target
        this.start = this.current
    }

    fun setSpeed(speed: Float) {
        this.speed = speed
    }

    fun isDone(): Boolean {
        return done
    }

    fun getEased(): Float {
        return eased
    }

    enum class EaseType {
        EASE_IN_SINE,
        EASE_OUT_SINE,
        EASE_IN_OUT_SINE,
        EASE_IN_QUAD,
        EASE_OUT_QUAD,
        EASE_IN_OUT_QUAD,
        EASE_IN_EXPO,
        EASE_OUT_EXPO,
        EASE_IN_OUT_EXPO
    }
}
