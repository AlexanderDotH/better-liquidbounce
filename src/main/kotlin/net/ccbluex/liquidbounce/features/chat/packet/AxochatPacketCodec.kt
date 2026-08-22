/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.chat.packet

import com.google.gson.GsonBuilder

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
        register<C2SRequestOnlineUsersPacket>("RequestOnlineUsers")
    }

    private val deserializer = PacketDeserializer().apply {
        register<S2CMojangInfoPacket>("MojangInfo")
        register<S2CNewJWTPacket>("NewJWT")
        register<S2CMessagePacket>("Message")
        register<S2CPrivateMessagePacket>("PrivateMessage")
        register<S2CErrorPacket>("Error")
        register<S2CSuccessPacket>("Success")
        register<S2COnlineUsersPacket>("OnlineUsers")
    }

    private val encoder = GsonBuilder()
        .registerTypeAdapter(AxochatPacket.C2S::class.java, serializer)
        .create()

    private val decoder = GsonBuilder()
        .registerTypeAdapter(AxochatPacket.S2C::class.java, deserializer)
        .create()

    fun encode(packet: AxochatPacket.C2S): String =
        encoder.toJson(packet, AxochatPacket.C2S::class.java)

    fun decode(message: String): AxochatPacket.S2C? =
        decoder.fromJson(message, AxochatPacket.S2C::class.java)
}
