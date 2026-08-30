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

import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.ccbluex.liquidbounce.integration.interop.forbidden
import net.ccbluex.liquidbounce.integration.interop.serviceUnavailable
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.toName
import net.ccbluex.liquidbounce.utils.item.getOrNull
import net.ccbluex.liquidbounce.utils.network.packetRegistry
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import kotlin.jvm.optionals.getOrNull

internal fun itemTag(name: String): TagKey<Item> =
    TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(name))

internal fun blockTag(name: String): TagKey<Block> =
    TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(name))

internal val ACCEPTED_ITEM_TAGS =
    arrayOf(
        itemTag("wool"),
        itemTag("planks"),
        itemTag("stone_bricks"),
        itemTag("buttons"),
        itemTag("wool_carpets"),
        itemTag("fence_gates"),
        itemTag("wooden_pressure_plates"),
        itemTag("doors"),
        itemTag("logs"),
        itemTag("banners"),
        itemTag("sand"),
        itemTag("stairs"),
        itemTag("slabs"),
        itemTag("walls"),
        itemTag("anvil"),
        itemTag("rails"),
        itemTag("small_flowers"),
        itemTag("saplings"),
        itemTag("leaves"),
        itemTag("trapdoors"),
        itemTag("beds"),
        itemTag("fences"),
        itemTag("gold_ores"),
        itemTag("iron_ores"),
        itemTag("diamond_ores"),
        itemTag("redstone_ores"),
        itemTag("lapis_ores"),
        itemTag("coal_ores"),
        itemTag("emerald_ores"),
        itemTag("copper_ores"),
        itemTag("candles"),
        itemTag("dirt"),
        itemTag("terracotta"),
        itemTag("boats"),
        itemTag("fishes"),
        itemTag("signs"),
        itemTag("creeper_drop_music_discs"),
        itemTag("coals"),
        itemTag("arrows"),
        itemTag("compasses"),
        itemTag("trim_materials"),
        itemTag("swords"),
        itemTag("axes"),
        itemTag("hoes"),
        itemTag("pickaxes"),
        itemTag("shovels"),
    )

internal val ACCEPTED_BLOCK_TAGS =
    arrayOf(
        blockTag("wool"),
        blockTag("planks"),
        blockTag("stone_bricks"),
        blockTag("buttons"),
        blockTag("wool_carpets"),
        blockTag("pressure_plates"),
        blockTag("doors"),
        blockTag("flowers"),
        blockTag("saplings"),
        blockTag("logs"),
        blockTag("banners"),
        blockTag("sand"),
        blockTag("stairs"),
        blockTag("slabs"),
        blockTag("walls"),
        blockTag("anvil"),
        blockTag("rails"),
        blockTag("leaves"),
        blockTag("trapdoors"),
        blockTag("beds"),
        blockTag("fences"),
        blockTag("gold_ores"),
        blockTag("iron_ores"),
        blockTag("diamond_ores"),
        blockTag("redstone_ores"),
        blockTag("lapis_ores"),
        blockTag("coal_ores"),
        blockTag("emerald_ores"),
        blockTag("copper_ores"),
        blockTag("candles"),
        blockTag("dirt"),
        blockTag("terracotta"),
        blockTag("flower_pots"),
        blockTag("ice"),
        blockTag("corals"),
        blockTag("all_signs"),
        blockTag("beehives"),
        blockTag("crops"),
        blockTag("portals"),
        blockTag("fire"),
        blockTag("nylium"),
        blockTag("shulker_boxes"),
        blockTag("campfires"),
        blockTag("fence_gates"),
        blockTag("cauldrons"),
        blockTag("snow"),
    )

internal fun <T : Any> constructMap(
    registry: DefaultedRegistry<T>,
    tagKeys: Array<TagKey<T>>,
): Map<Identifier, Identifier> {
    val map = Object2ObjectOpenHashMap<Identifier, Identifier>()

    for (acceptedTag in tagKeys) {
        val get = registry.get(acceptedTag).getOrNull() ?: continue

        get.forEach {
            val itemId = registry.getKey(it.value())

            val prev = map.putIfAbsent(itemId, acceptedTag.location)
            if (prev != null) {
                logger.warn("Duplicate $itemId in ${acceptedTag.location} in $prev")

                return@forEach
            }
        }
    }

    return map
}

internal inline fun <T : Any> Registry<T>.buildOutput(
    name: (Identifier, T) -> String,
    iconUrl: (Identifier) -> String? = { null },
): Map<String, RegistryItemOutput> {
    val obj = Object2ObjectOpenHashMap<String, RegistryItemOutput>(this.size())
    for (item in this) {
        val id = this.getKey(item) ?: continue
        obj[id.toString()] = RegistryItemOutput(name(id, item), iconUrl(id))
    }
    return obj
}

@JvmRecord
internal data class RegistryItemOutput(val name: String, val icon: String?)

internal fun localizedItemRegistryName(
    identifier: Identifier,
    translationKey: String?,
    translate: (String) -> String = { Component.translatable(it).string },
): String {
    val fallback = identifier.toName()
    val key = translationKey ?: return fallback
    return translate(key).takeUnless { it.isBlank() || it == key } ?: fallback
}
