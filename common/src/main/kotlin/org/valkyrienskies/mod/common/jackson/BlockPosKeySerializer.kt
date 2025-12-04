package org.valkyrienskies.mod.common.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import net.minecraft.core.BlockPos
import java.io.IOException

class BlockPosKeySerializer : JsonSerializer<BlockPos>() {
    @Throws(IOException::class)
    override fun serialize(value: BlockPos, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeFieldName(value.asLong().toString())
    }
}
