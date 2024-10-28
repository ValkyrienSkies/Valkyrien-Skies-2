package org.valkyrienskies.mod.common.util

data class SplittingDisablerAttachment(private var splitt: Boolean) {
    fun enableSplitting() {
        splitt = true
    }

    fun disableSplitting() {
        splitt = false
    }

    fun canSplit(): Boolean {
        return splitt
    }
}
