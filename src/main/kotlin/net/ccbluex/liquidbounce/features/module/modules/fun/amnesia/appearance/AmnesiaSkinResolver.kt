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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.appearance

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.core.HttpMethod
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.api.thirdparty.lookupUuidByName
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.clientIdentifier
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.ccbluex.liquidbounce.utils.render.readNativeImage
import net.ccbluex.liquidbounce.utils.render.registerTexture
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.core.ClientAsset
import net.minecraft.core.UUIDUtil
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

internal class AmnesiaSkinResolver {

    private val loadingSkinNames = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var cachedSkin = SkinCache("", null, null)

    fun resolve(name: String, nameMcSkinInput: String): PlayerSkin? {
        val skinId = parseNameMcSkinId(nameMcSkinInput)
        if (skinId == null) {
            findOnlineSkin(name)?.let { return it }
        }

        val cached = cachedSkin
        if (cached.matches(name, skinId)) {
            return cached.supplier?.get()
        }

        requestSkinLookup(name, skinId)
        return null
    }

    fun parseNameMcSkinId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        return SKIN_ID_PATTERN.find(trimmed)?.groupValues?.get(1)?.lowercase()
    }

    private fun findOnlineSkin(name: String): PlayerSkin? =
        mc.connection?.onlinePlayers?.firstOrNull {
            it.profile.name.equals(name, ignoreCase = true)
        }?.skin

    private fun requestSkinLookup(name: String, skinId: String?) {
        val key = skinCacheKey(name, skinId)
        if (!loadingSkinNames.add(key)) {
            return
        }

        ioScope.launch {
            try {
                cachedSkin = SkinCache(name, skinId, loadSkinSupplier(name, skinId))
            } finally {
                loadingSkinNames.remove(key)
            }
        }
    }

    private suspend fun loadSkinSupplier(name: String, skinId: String?): Supplier<PlayerSkin> {
        val profile = resolveSkinProfile(name)
        loadDownloadedSkinSupplier(profile, skinId)?.let { return it }
        return PlayerInfo.createSkinLookup(profile.gameProfile)
    }

    private suspend fun resolveSkinProfile(name: String): ResolvedSkinProfile = withContext(Dispatchers.IO) {
        val uuid = runCatching { lookupUuidByName(name) }.getOrNull()
            ?: UUIDUtil.createOfflineProfile(name).id
        val profile = mc.services.sessionService.fetchProfile(uuid, false)
        val gameProfile = profile?.profile ?: GameProfile(uuid, name)
        val skinTexture = profile?.let {
            mc.services.sessionService.unpackTextures(mc.services.sessionService.getPackedTextures(it.profile)).skin
        }
        ResolvedSkinProfile(gameProfile, skinTexture?.url, skinTexture?.getMetadata("model").toPlayerModelType())
    }

    private suspend fun loadDownloadedSkinSupplier(
        profile: ResolvedSkinProfile,
        skinId: String?,
    ): Supplier<PlayerSkin>? {
        val url = skinId?.let { NAME_MC_SKIN_URL.format(it) } ?: profile.skinUrl ?: return null
        return runCatching {
            val identifier = clientIdentifier("amnesia/appearance/skin-${Integer.toHexString(url.hashCode())}")
            val nativeImage = withContext(Dispatchers.IO) {
                HttpClient.request(url, HttpMethod.GET).use { response ->
                    check(response.body.contentLength() != 0L) { "Empty skin response" }
                    response.body.source().readNativeImage()
                }
            }
            withContext(Dispatchers.Minecraft) { nativeImage.registerTexture(identifier) }
            Supplier { PlayerSkin(RegisteredSkinTexture(identifier), null, null, profile.model, false) }
        }.getOrNull()
    }

    private fun skinCacheKey(name: String, skinId: String?) = "${name.lowercase()}|${skinId.orEmpty()}"

    private fun String?.toPlayerModelType() = if (this == PlayerModelType.SLIM.serializedName) {
        PlayerModelType.SLIM
    } else {
        PlayerModelType.WIDE
    }

    private data class SkinCache(val name: String, val skinId: String?, val supplier: Supplier<PlayerSkin>?) {
        fun matches(otherName: String, otherSkinId: String?) =
            name.equals(otherName, ignoreCase = true) && skinId == otherSkinId
    }

    private data class ResolvedSkinProfile(
        val gameProfile: GameProfile,
        val skinUrl: String?,
        val model: PlayerModelType,
    )

    private class RegisteredSkinTexture(private val identifier: Identifier) : ClientAsset.Texture {
        override fun id(): Identifier = identifier
        override fun texturePath(): Identifier = identifier
    }

    private companion object {
        const val NAME_MC_SKIN_URL = "https://s.namemc.com/i/%s.png"
        val SKIN_ID_PATTERN = Regex("""(?:^|/)([0-9a-fA-F]{8,64})(?:\.png)?(?:$|[?#])""")
    }
}
