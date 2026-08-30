/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.chat

import com.google.gson.GsonBuilder
import net.ccbluex.liquidbounce.features.chat.packet.AxochatPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SBanUserPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginMojangPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SPrivateMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestMojangInfoPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SUnbanUserPacket
import net.ccbluex.liquidbounce.features.chat.packet.PacketDeserializer
import net.ccbluex.liquidbounce.features.chat.packet.PacketSerializer
import net.ccbluex.liquidbounce.features.chat.packet.S2CErrorPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CMojangInfoPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CNewJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CPrivateMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CSuccessPacket

internal class AxochatPacketCodec {

    private val serializer = PacketSerializer().apply {
        register<C2SRequestMojangInfoPacket>("RequestMojangInfo")
        register<C2SLoginMojangPacket>("LoginMojang")
        register<C2SMessagePacket>("Message")
        register<C2SPrivateMessagePacket>("PrivateMessage")
        register<C2SBanUserPacket>("BanUser")
        register<C2SUnbanUserPacket>("UnbanUser")
        register<C2SRequestJWTPacket>("RequestJWT")
        register<C2SLoginJWTPacket>("LoginJWT")
    }

    private val deserializer = PacketDeserializer().apply {
        register<S2CMojangInfoPacket>("MojangInfo")
        register<S2CNewJWTPacket>("NewJWT")
        register<S2CMessagePacket>("Message")
        register<S2CPrivateMessagePacket>("PrivateMessage")
        register<S2CErrorPacket>("Error")
        register<S2CSuccessPacket>("Success")
    }

    private val serializerGson by lazy {
        GsonBuilder()
            .registerTypeAdapter(AxochatPacket.C2S::class.java, serializer)
            .create()
    }

    private val deserializerGson by lazy {
        GsonBuilder()
            .registerTypeAdapter(AxochatPacket.S2C::class.java, deserializer)
            .create()
    }

    fun encode(packet: AxochatPacket.C2S): String =
        serializerGson.toJson(packet, AxochatPacket.C2S::class.java)

    fun decode(message: String): AxochatPacket.S2C =
        deserializerGson.fromJson(message, AxochatPacket.S2C::class.java)
}
