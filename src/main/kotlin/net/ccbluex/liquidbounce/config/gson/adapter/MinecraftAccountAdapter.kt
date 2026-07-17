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

package net.ccbluex.liquidbounce.config.gson.adapter

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.ccbluex.liquidbounce.LiquidBounce.logger
import net.ccbluex.liquidbounce.authlib.account.AlteningAccount
import net.ccbluex.liquidbounce.authlib.account.CrackedAccount
import net.ccbluex.liquidbounce.authlib.account.MinecraftAccount
import net.ccbluex.liquidbounce.authlib.compat.GameProfile
import java.lang.reflect.Type

object MinecraftAccountAdapter : JsonSerializer<MinecraftAccount>, JsonDeserializer<MinecraftAccount> {

    private const val ALTENING_ACCOUNT_TYPE = "AlteningAccount"
    private const val ALTENING_ACCOUNT_TOKEN = "accountToken"
    private const val ALTENING_ACCOUNT_PENDING = "pending"

    override fun serialize(src: MinecraftAccount, typeOfSrc: Type, context: JsonSerializationContext) =
        src.toJson().apply {
            if (src is AlteningAccount && src.accountToken.isNotBlank()) {
                addProperty(ALTENING_ACCOUNT_TOKEN, src.accountToken)
                addProperty(ALTENING_ACCOUNT_PENDING, src.profile?.uuid == null)
            }
        }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext?) = try {
        val jsonObject = json.asJsonObject
        if (jsonObject.isPendingAlteningAccount()) {
            deserializePendingAlteningAccount(jsonObject)
        } else {
            MinecraftAccount.fromJson(jsonObject).apply {
                restoreAlteningAccountToken(jsonObject)
            }
        }
    } catch (e: Exception) {
        logger.error("Failed to deserialize MinecraftAccount from JSON.", e)
        CrackedAccount("Error${json.hashCode()}")
    }

    private fun JsonObject.isPendingAlteningAccount() =
        get("type")?.asString == ALTENING_ACCOUNT_TYPE && get(ALTENING_ACCOUNT_PENDING)?.asBoolean == true

    private fun deserializePendingAlteningAccount(json: JsonObject): AlteningAccount {
        val accountToken = json.requireString(ALTENING_ACCOUNT_TOKEN)
        val username = json.requireString("name")

        return AlteningAccount(accountToken).apply {
            profile = GameProfile(username, null)
            if (json["favorite"]?.asBoolean == true) {
                favorite()
            }
        }
    }

    private fun MinecraftAccount.restoreAlteningAccountToken(json: JsonObject) {
        if (this is AlteningAccount) {
            json[ALTENING_ACCOUNT_TOKEN]?.asString?.takeIf { it.isNotBlank() }?.let {
                accountToken = it
            }
        }
    }

    private fun JsonObject.requireString(name: String) = get(name)?.asString?.takeIf { it.isNotBlank() }
        ?: error("Missing $name for pending TheAltening account")

}
