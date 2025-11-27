package org.valkyrienskies.mod.common

import org.valkyrienskies.core.internal.VsiCore

interface VSCoreProvider {
    fun newVSCore(): VsiCore
}
