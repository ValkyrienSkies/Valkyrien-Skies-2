package org.valkyrienskies.mod.forge.common

import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLPaths
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.CommonHooksImpl
import java.nio.file.Path

object ForgeHooksImpl : CommonHooksImpl() {

    override val isPhysicalClient: Boolean
        get() = FMLEnvironment.dist.isClient

    override val configDir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ValkyrienSkiesMod.MOD_ID)
}
