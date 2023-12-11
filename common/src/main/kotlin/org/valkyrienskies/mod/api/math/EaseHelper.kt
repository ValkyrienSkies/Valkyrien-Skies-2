package org.valkyrienskies.mod.api.math

import kotlin.math.pow

object EaseHelper {
    /**
     * Ease In Sine + Ease Out Sine + Ease In/Out Sine
     */
    //Sine In
    fun easeInSine(x: Float): Float {
        return (1 - Math.cos(x * Math.PI / 2)).toFloat()
    }

    //Sine Out
    fun easeOutSine(x: Float): Float {
        return Math.sin(x * Math.PI / 2).toFloat()
    }

    //Sine In/Out
    fun easeInOutSine(x: Float): Float {
        return (-(Math.cos(Math.PI * x) - 1) / 2).toFloat()
    }

    /**
     * Ease In Quad + Ease Out Quad + Ease In/Out Quad
     */
    //Quad In
    fun easeInQuad(x: Float): Float {
        return x * x
    }

    //Quad Out
    fun easeOutQuad(x: Float): Float {
        return 1 - (1 - x) * (1 - x)
    }

    //Quad In/Out
    fun easeInOutQuad(x: Float): Float {
        return if (x < 0.5) 2 * x * x else (1 - Math.pow((-2 * x + 2).toDouble(), 2.0) / 2).toFloat()
    }

    /**
     * Ease In Exponential + Ease Out Exponential + Ease In/Out Exponential
     */

    //Exponential In
    fun easeInExpo(x: Float): Float {
        return if (x.toInt() == 0) {
            0.0f
        } else {
            ((10 * x - 10).pow(2))
        }
    }

    //Exponential Out
    fun easeOutExpo(x: Float): Float {
        return if (x == 1.0f) { 1.0f } else {(1 - 2.0.pow(-10 * x.toInt())).toFloat()}
    }

    //Exponential In/Out
    fun easeInOutExpo(x: Float): Float {
        return if (x == 0.0f) {
            0.0f
        } else if (x == 1.0f) {
            1.0f
        } else if (x < 0.5) {
            2.0.pow(20 * x.toInt() - 10).toFloat() / 2
        } else{
            (2 - 2.0.pow(-20 * x.toInt() + 10)).toFloat() / 2
        }
    }
}
