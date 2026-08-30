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
package net.ccbluex.liquidbounce.features.account

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.ccbluex.liquidbounce.config.gson.ConfigGsonAdapterRegistry
import net.ccbluex.liquidbounce.utils.client.logger
import net.minecraft.client.User
import java.lang.reflect.Type

object MinecraftAccountGsonAdapter : JsonSerializer<MinecraftAccount>, JsonDeserializer<MinecraftAccount> {

    private const val ALTENING_ACCOUNT_TYPE = "AlteningAccount"
    private const val ALTENING_ACCOUNT_TOKEN = "accountToken"
    private const val ALTENING_ACCOUNT_PENDING = "pending"
    private const val WORKING_SERVERS = "workingServers"
    private var installed = false

    @Synchronized
    fun install() {
        check(!installed) { "Minecraft account Gson adapter is already installed" }
        ConfigGsonAdapterRegistry.install {
            registerTypeHierarchyAdapter(MinecraftAccount::class.javaObjectType, MinecraftAccountGsonAdapter)
            registerTypeHierarchyAdapter(User::class.javaObjectType, SessionSerializer)
        }
        installed = true
    }

    override fun serialize(src: MinecraftAccount, typeOfSrc: Type, context: JsonSerializationContext) =
        src.toJson().apply {
            add(WORKING_SERVERS, context.serialize(AccountServerAccessRegistry.list(src)))
            if (src is AlteningAccount && src.accountToken.isNotBlank()) {
                addProperty(ALTENING_ACCOUNT_TOKEN, src.accountToken)
                addProperty(ALTENING_ACCOUNT_PENDING, src.profile == null)
            }
        }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext?) = try {
        val jsonObject = json.asJsonObject
        val account = if (jsonObject.isPendingAlteningAccount()) {
            deserializePendingAlteningAccount(jsonObject)
        } else {
            MinecraftAccount.fromJson(jsonObject).apply { restoreAlteningAccountToken(jsonObject) }
        }
        account.restoreWorkingServers(jsonObject)
        account
    } catch (error: Exception) {
        logger.error("Failed to deserialize MinecraftAccount (${error::class.simpleName}).")
        CrackedAccount("Error${json.hashCode()}")
    }

    private fun JsonObject.isPendingAlteningAccount() =
        get("type")?.asString == ALTENING_ACCOUNT_TYPE && get(ALTENING_ACCOUNT_PENDING)?.asBoolean == true

    private fun deserializePendingAlteningAccount(json: JsonObject): AlteningAccount {
        val accountToken = json.requireString(ALTENING_ACCOUNT_TOKEN)
        val username = json.requireString("name")
        return pendingAlteningAccount(accountToken, username).apply {
            favorite = json["favorite"]?.asBoolean == true
        }
    }

    private fun MinecraftAccount.restoreAlteningAccountToken(json: JsonObject) {
        if (this is AlteningAccount) {
            json[ALTENING_ACCOUNT_TOKEN]?.asString?.takeIf(String::isNotBlank)?.let { accountToken = it }
        }
    }

    private fun MinecraftAccount.restoreWorkingServers(json: JsonObject) {
        val serverNames = json[WORKING_SERVERS]
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.mapNotNull { element -> runCatching(element::getAsString).getOrNull() }
            .orEmpty()
        AccountServerAccessRegistry.restore(this, serverNames)
    }

    private fun JsonObject.requireString(name: String) = get(name)?.asString?.takeIf(String::isNotBlank)
        ?: error("Missing $name for pending TheAltening account")
}
