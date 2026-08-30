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



@file:JvmName("RegistryFunctionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import com.google.common.base.CaseFormat
import com.google.gson.JsonObject
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.minecraft.client.player.RemotePlayer
import net.ccbluex.liquidbounce.integration.interop.ClientInteropServer
import net.ccbluex.liquidbounce.integration.interop.forbidden
import net.ccbluex.liquidbounce.integration.interop.serviceUnavailable
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.toName
import net.ccbluex.liquidbounce.utils.item.getOrNull
import net.ccbluex.liquidbounce.utils.network.packetRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.resources.Identifier
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Items
import java.util.Locale
import kotlin.jvm.optionals.getOrNull

// GET /api/v1/client/registry/{name}
internal fun Route.getRegistry() = get {
    val registryName = call.parameters["name"]
        ?: call.forbidden("Missing registry name parameter")
    call.respond(call.registryOutput(registryName))
}

private suspend fun ApplicationCall.registryOutput(name: String): Map<String, RegistryItemOutput> =
    when (name.lowercase(Locale.ENGLISH)) {
        "blocks", "block" -> BuiltInRegistries.BLOCK.buildOutput(
            name = { _, block -> block.name.string },
            iconUrl = ::itemIconUrl,
        )
        "items", "item" -> BuiltInRegistries.ITEM.buildOutput(
            name = { id, item -> localizedItemRegistryName(id, item.descriptionId) },
            iconUrl = ::itemIconUrl,
        )
        "sounds", "sound_event" -> soundRegistryOutput()
        "mob_effect" -> BuiltInRegistries.MOB_EFFECT.buildOutput(
            name = { _, effect -> effect.displayName.string },
            iconUrl = ::effectTextureUrl,
        )
        "enchantment" -> enchantmentRegistryOutput()
        "c2s_packet" -> packetRegistryOutput(PacketFlow.SERVERBOUND)
        "s2c_packet" -> packetRegistryOutput(PacketFlow.CLIENTBOUND)
        "entity_type" -> BuiltInRegistries.ENTITY_TYPE.buildOutput(name = { _, type -> type.description.string })
        "screen_handler", "menu" -> menuRegistryOutput()
        "client_module" -> ModuleManager.associate { it.name to RegistryItemOutput(it.name, null) }
        "world_players" -> worldPlayerRegistryOutput()
        else -> forbidden("Invalid registry name: $name")
    }

private fun soundRegistryOutput(): Map<String, RegistryItemOutput> {
    val icon = itemIconUrl(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_13))
    return BuiltInRegistries.SOUND_EVENT.buildOutput(name = { _, sound -> sound.location.toName() }) { icon }
}

private suspend fun ApplicationCall.enchantmentRegistryOutput(): Map<String, RegistryItemOutput> {
    val registry = Registries.ENCHANTMENT.getOrNull() ?: serviceUnavailable("Registry not loaded")
    return registry.buildOutput(name = { _, enchantment -> enchantment.description.string })
}

private fun packetRegistryOutput(flow: PacketFlow): Map<String, RegistryItemOutput> = packetRegistry[flow]!!.associate {
    it.toString() to RegistryItemOutput(it.toName(), null)
}

private fun menuRegistryOutput(): Map<String, RegistryItemOutput> {
    val converter = CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL)
    return BuiltInRegistries.MENU.buildOutput(name = { id, _ -> converter.convert(id.toName())!! })
}

private fun worldPlayerRegistryOutput(): Map<String, RegistryItemOutput> = mc.level?.players()
    ?.asSequence()
    ?.filterIsInstance<RemotePlayer>()
    ?.filter { it !== mc.player }
    ?.filterNot { ModuleAntiBot.isBot(it) }
    ?.associate { player -> player.gameProfile.name.let { it to RegistryItemOutput(it, null) } }
    ?: emptyMap()

private fun itemIconUrl(id: Identifier) = "${ClientInteropServer.url}/api/v1/client/resource/itemTexture?id=$id"

private fun effectTextureUrl(id: Identifier) = "${ClientInteropServer.url}/api/v1/client/resource/effectTexture?id=$id"


// GET /api/v1/client/registry/{name}/groups
internal fun Route.getRegistryGroups() = get("/groups") {
    val name = call.parameters["name"] ?: call.forbidden("Missing registry name parameter")
    call.respond(call.registryGroups(name))
}

private suspend fun ApplicationCall.registryGroups(name: String): JsonObject = when (name.lowercase(Locale.ENGLISH)) {
    "items" -> itemRegistryGroups()
    "blocks" -> blockRegistryGroups()
    else -> forbidden("Invalid registry name: $name")
}

private fun itemRegistryGroups() = JsonObject().apply {
    constructMap(BuiltInRegistries.ITEM, ACCEPTED_ITEM_TAGS).forEach { (id, group) ->
        add(id.toString(), registryRelation("group", group))
    }
}

private suspend fun ApplicationCall.blockRegistryGroups(): JsonObject {
    val world = mc.level ?: forbidden("No world")
    val parents = mutableMapOf<Identifier, Identifier>()
    BuiltInRegistries.BLOCK.forEach { block ->
        val stack = block.getCloneItemStack(world, BlockPos.ZERO, block.defaultBlockState(), false)
        val id = BuiltInRegistries.BLOCK.getKey(block)
        val item = stack.item
        if (item is BlockItem && item.block != block) {
            parents[id] = BuiltInRegistries.BLOCK.getKey(item.block)
        } else if (item !is BlockItem && !stack.isEmpty) {
            logger.warn("Invalid pick stack for $id: $stack")
        }
    }
    return mergeBlockRelations(parents, constructMap(BuiltInRegistries.BLOCK, ACCEPTED_BLOCK_TAGS))
}

private fun mergeBlockRelations(
    parents: Map<Identifier, Identifier>,
    groups: Map<Identifier, Identifier>,
) = JsonObject().apply {
    BuiltInRegistries.BLOCK.forEach { block ->
        val id = BuiltInRegistries.BLOCK.getKey(block)
        val relation = parents[id]?.let { registryRelation("parent", it) }
            ?: groups[id]?.let { registryRelation("group", it) }
            ?: return@forEach
        add(id.toString(), relation)
    }
}

private fun registryRelation(relation: String, relative: Identifier) = JsonObject().apply {
    addProperty("relation", relation)
    addProperty("relative", relative.toString())
}
