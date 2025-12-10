package org.valkyrienskies.mod.fabric.compat.hexcasting

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.compat.hexcasting.AmbitRemapping
import org.valkyrienskies.mod.compat.hexcasting.hextweaks.HexTweaksCompat

class FabricShipAmbit(env: CastingEnvironment) : AmbitRemapping(env) {
    override fun getCasterPosition(): Vec3? {
        super.getCasterPosition()?.let { return it }

        if (FabricLoader.getInstance().isModLoaded("hextweaks"))
            HexTweaksCompat.getComputerPosition(env)?.let { return it }

        return null
    }
}
