package org.valkyrienskies.mod.forge.compat.hexcasting

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import net.minecraft.world.phys.Vec3
import net.minecraftforge.fml.ModList
import org.valkyrienskies.mod.compat.hexcasting.AmbitRemapping
import org.valkyrienskies.mod.compat.hexcasting.hextweaks.HexTweaksCompat

class ForgeShipAmbit(env: CastingEnvironment) : AmbitRemapping(env) {
    override fun getCasterPosition(): Vec3? {
        super.getCasterPosition()?.let { return it }

        if (ModList.get().isLoaded("hextweaks"))
            HexTweaksCompat.getComputerPosition(env)?.let { return it }

        return null
    }
}
