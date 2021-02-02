package org.valkyrienskies.mod.networking.impl

import io.netty.buffer.ByteBuf
import net.minecraft.network.FriendlyByteBuf
import org.valkyrienskies.core.game.ShipData
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.util.serialization.VSJacksonUtil

class VSPacketShipDataList private constructor(): IVSPacket {

    private lateinit var shipDataList: List<ShipData>

    fun getShipDataList() = shipDataList

    override fun write(byteBuf: ByteBuf) {
        val friendlyByteBuf = FriendlyByteBuf(byteBuf)
        friendlyByteBuf.writeVarInt(shipDataList.size)
        for (shipData in shipDataList) {
            val bytes = VSJacksonUtil.defaultMapper.writeValueAsBytes(shipData)
            friendlyByteBuf.writeByteArray(bytes)
        }
    }

    override fun read(byteBuf: ByteBuf) {
        val newShipDataList: MutableList<ShipData> = ArrayList()
        val friendlyByteBuf = FriendlyByteBuf(byteBuf)
        val listSize: Int = friendlyByteBuf.readVarInt()
        for (i in 1 .. listSize) {
            val bytes = friendlyByteBuf.readByteArray()
            val shipDataFromBytes = VSJacksonUtil.defaultMapper.readValue(bytes, ShipData::class.java)
            newShipDataList.add(shipDataFromBytes)
        }
        shipDataList = newShipDataList
    }

    companion object {
        fun createVSPacketShipDataList(shipDataCollection: Collection<ShipData>): VSPacketShipDataList {
            val packet = VSPacketShipDataList()
            packet.shipDataList = ArrayList(shipDataCollection)
            return packet
        }

        fun createEmptyVSPacketShipDataList(): VSPacketShipDataList {
            return VSPacketShipDataList()
        }
    }
}