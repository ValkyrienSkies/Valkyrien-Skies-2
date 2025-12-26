package org.valkyrienskies.mod.common.jackson

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.ser.std.StdKeySerializers
import net.minecraft.core.BlockPos

class BlockPosKeyDeserializer: KeyDeserializer() {
    override fun deserializeKey(key: String?, ctxt: DeserializationContext): Any? {
        if (key == null) return null

        return try {
            val packed = key.toLong()
            BlockPos.of(packed)
        } catch (e: NumberFormatException) {
            throw JsonMappingException.from(ctxt, "Invalid BlockPos key '$key'", e)
        }
    }
}
