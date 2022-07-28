package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.EnvType.CLIENT
import net.fabricmc.loader.api.FabricLoader
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.CommonHooksImpl
import java.nio.file.Path

object FabricHooksImpl : CommonHooksImpl() {

    override val isPhysicalClient: Boolean
        get() = FabricLoader.getInstance().environmentType == CLIENT

    override val configDir: Path
        get() = FabricLoader.getInstance().configDir.resolve(ValkyrienSkiesMod.MOD_ID)
}
