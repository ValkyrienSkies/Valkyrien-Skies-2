package org.valkyrienskies.mod.quilt.common

import net.fabricmc.api.EnvType.CLIENT
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.CommonHooksImpl
import java.nio.file.Path

object QuiltHooksImpl : CommonHooksImpl() {

    override val isPhysicalClient: Boolean
        get() = MinecraftQuiltLoader.getEnvironmentType() == CLIENT

    override val configDir: Path
        get() = QuiltLoader.getConfigDir().resolve(ValkyrienSkiesMod.MOD_ID)
}
