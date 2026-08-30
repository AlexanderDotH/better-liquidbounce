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
package net.ccbluex.liquidbounce.event.events

import net.ccbluex.liquidbounce.annotations.Tag
import net.ccbluex.liquidbounce.common.interop.PlayerDataPayload
import net.ccbluex.liquidbounce.common.interop.PlayerInventoryDataPayload
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.event.Event
import net.ccbluex.liquidbounce.event.WebSocketEvent
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.PlayerData
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.PlayerInventoryData
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class PlayerPayloadEventContractTest {

    @Test
    fun `player events expose neutral payload contracts and stable tags`() {
        assertSame(PlayerDataPayload::class.java, ClientPlayerDataEvent::class.java.getDeclaredField("playerData").type)
        assertSame(
            PlayerInventoryDataPayload::class.java,
            ClientPlayerInventoryEvent::class.java.getDeclaredField("inventory").type,
        )
        assertSame(PlayerDataPayload::class.java, TargetChangeEvent::class.java.getDeclaredField("target").type)

        assertEquals("clientPlayerData", ClientPlayerDataEvent::class.java.getAnnotation(Tag::class.java).name)
        assertEquals(
            "clientPlayerInventory",
            ClientPlayerInventoryEvent::class.java.getAnnotation(Tag::class.java).name,
        )
        assertEquals("targetChange", TargetChangeEvent::class.java.getAnnotation(Tag::class.java).name)
        assertTrue(Event::class.java.isAssignableFrom(ClientPlayerDataEvent::class.java))
        assertTrue(WebSocketEvent::class.java.isAssignableFrom(ClientPlayerDataEvent::class.java))
    }

    @Test
    fun `concrete payloads retain record field names types and order`() {
        assertEquals(PLAYER_FIELDS, instanceFields(PlayerData::class.java))
        assertEquals(INVENTORY_FIELDS, instanceFields(PlayerInventoryData::class.java))
        assertTrue(PlayerDataPayload::class.java.isAssignableFrom(PlayerData::class.java))
        assertTrue(PlayerInventoryDataPayload::class.java.isAssignableFrom(PlayerInventoryData::class.java))
    }

    @Test
    fun `runtime gson retains direct rest and event payload envelopes`() {
        MinecraftBootstrap.ensureInitialized()
        val player = SerializablePlayerPayload()
        val directPlayer = interopGson.toJsonTree(player).asJsonObject
        val inventory = PlayerInventoryData(emptyList(), emptyList(), emptyList(), emptyList())
        val directInventory = interopGson.toJsonTree(inventory).asJsonObject

        assertEquals(PLAYER_FIELDS.map { it.first }, directPlayer.keySet().toList())
        assertEquals(INVENTORY_FIELDS.map { it.first }, directInventory.keySet().toList())
        assertEquals(
            directPlayer,
            eventJson(ClientPlayerDataEvent(player))["playerData"],
        )
        assertEquals(
            directPlayer,
            eventJson(TargetChangeEvent(player))["target"],
        )
        assertFalse(eventJson(TargetChangeEvent(null)).has("target"))
        assertEquals(
            directInventory,
            eventJson(ClientPlayerInventoryEvent(inventory))["inventory"],
        )
    }

    @Test
    fun `rest routes continue responding with concrete payloads`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/game/PlayerFunctions.kt"
        ))

        assertTrue(source.contains("mc.player?.let(PlayerData::fromPlayer)"))
        assertTrue(source.contains("mc.player?.let(PlayerInventoryData::fromPlayer)"))
        assertEquals(2, source.split("call.respond(player").size - 1)
        assertFalse(source.contains("MixinHudAccessor"))
    }

    private fun instanceFields(type: Class<*>): List<Pair<String, Class<*>>> = type.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .map { it.name to it.type }

    private fun eventJson(event: Event) = (event as WebSocketEvent).serializer
        .toJsonTree(event, event.javaClass)
        .asJsonObject

    private data class SerializablePlayerPayload(
        val username: String = "Alex",
        val uuid: String = "00000000-0000-0000-0000-000000000000",
        val dimension: String = "minecraft:overworld",
        val position: List<Double> = listOf(1.0, 64.0, 2.0),
        val netherPosition: List<Double> = listOf(0.125, 64.0, 0.25),
        val blockPosition: List<Int> = listOf(1, 64, 2),
        val velocity: List<Double> = listOf(0.0, 0.0, 0.0),
        val selectedSlot: Int = 2,
        val gameMode: String = "survival",
        val health: Float = 20f,
        val actualHealth: Float = 20f,
        val maxHealth: Float = 20f,
        val absorption: Float = 0f,
        val yaw: Float = 90f,
        val pitch: Float = 10f,
        val armor: Int = 5,
        val food: Int = 20,
        val air: Int = 300,
        val maxAir: Int = 300,
        val experienceLevel: Int = 12,
        val experienceProgress: Float = 0.5f,
        val ping: Int = 42,
        val effects: List<Any> = emptyList(),
        val mainHandStack: Map<String, Any> = emptyMap(),
        val offHandStack: Map<String, Any> = emptyMap(),
        val armorItems: List<Any> = emptyList(),
        val scoreboard: Map<String, Any> = mapOf("header" to "Scoreboard", "entries" to emptyList<Any>()),
    ) : PlayerDataPayload

    private companion object {
        val PLAYER_FIELDS = listOf(
            "username" to String::class.java,
            "uuid" to String::class.java,
            "dimension" to net.minecraft.resources.Identifier::class.java,
            "position" to net.minecraft.world.phys.Vec3::class.java,
            "netherPosition" to net.minecraft.world.phys.Vec3::class.java,
            "blockPosition" to net.minecraft.core.BlockPos::class.java,
            "velocity" to net.minecraft.world.phys.Vec3::class.java,
            "selectedSlot" to Int::class.javaPrimitiveType!!,
            "gameMode" to net.minecraft.world.level.GameType::class.java,
            "health" to Float::class.javaPrimitiveType!!,
            "actualHealth" to Float::class.javaPrimitiveType!!,
            "maxHealth" to Float::class.javaPrimitiveType!!,
            "absorption" to Float::class.javaPrimitiveType!!,
            "yaw" to Float::class.javaPrimitiveType!!,
            "pitch" to Float::class.javaPrimitiveType!!,
            "armor" to Int::class.javaPrimitiveType!!,
            "food" to Int::class.javaPrimitiveType!!,
            "air" to Int::class.javaPrimitiveType!!,
            "maxAir" to Int::class.javaPrimitiveType!!,
            "experienceLevel" to Int::class.javaPrimitiveType!!,
            "experienceProgress" to Float::class.javaPrimitiveType!!,
            "ping" to Int::class.javaPrimitiveType!!,
            "effects" to List::class.java,
            "mainHandStack" to net.minecraft.world.item.ItemStack::class.java,
            "offHandStack" to net.minecraft.world.item.ItemStack::class.java,
            "armorItems" to List::class.java,
            "scoreboard" to net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ScoreboardData::class.java,
        )
        val INVENTORY_FIELDS = listOf(
            "armor" to List::class.java,
            "main" to List::class.java,
            "crafting" to List::class.java,
            "enderChest" to List::class.java,
        )
    }
}
