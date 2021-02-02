package net.examplemod.networking.impl

import io.netty.buffer.ByteBuf
import net.examplemod.networking.IVSPacket
import net.minecraft.network.FriendlyByteBuf
import org.valkyrienskies.core.game.ShipData
import org.valkyrienskies.core.util.serialization.VSJacksonUtil

class VSPacketShipDataList private constructor(): IVSPacket {

    private lateinit var shipDataList: List<ShipData>

    override fun write(byteBuf: ByteBuf) {
        val friendlyByteBuf = FriendlyByteBuf(byteBuf)
        friendlyByteBuf.writeVarInt(shipDataList.size)
        for (shipData in shipDataList) {
            val bytes = VSJacksonUtil.packetMapper.writeValueAsBytes(shipData)
            friendlyByteBuf.writeByteArray(bytes)
        }
    }

    override fun read(byteBuf: ByteBuf) {
        val newShipDataList: MutableList<ShipData> = ArrayList()
        val friendlyByteBuf = FriendlyByteBuf(byteBuf)
        val listSize: Int = friendlyByteBuf.readVarInt()
        for (i in 1 .. listSize) {
            val bytes = friendlyByteBuf.readByteArray()
            val shipDataFromBytes = VSJacksonUtil.packetMapper.readValue(bytes, ShipData::class.java)
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