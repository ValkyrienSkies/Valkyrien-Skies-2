package org.valkyrienskies.mod.forge.client

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.forge.autotest.AutoTestHarness

class ValkyrienSkiesModForgeClient {
    companion object {
        @JvmStatic
        fun clientInit(event: FMLClientSetupEvent) {
            // Put anything initialized on forge-side client here.
            ValkyrienSkiesMod.initClient()
            // No-op unless the vs.autotest system property names a script; see autotest/run.sh.
            AutoTestHarness.install()
        }
    }
}
