package org.valkyrienskies.mod.common.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.LongNode
import net.minecraft.core.BlockPos
import java.io.IOException

class BlockPosDeserializer : JsonDeserializer<BlockPos>() {
    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BlockPos {
        val node = p.getCodec().readTree<TreeNode>(p)

        val long = (node["long"] as LongNode).numberValue() as Long

        return BlockPos.of(long)
    }
}
