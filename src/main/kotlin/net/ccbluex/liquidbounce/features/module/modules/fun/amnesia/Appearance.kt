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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.core.HttpMethod
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.authlib.utils.generateOfflinePlayerUuid
import net.ccbluex.liquidbounce.authlib.yggdrasil.GameProfileRepository
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.ccbluex.liquidbounce.utils.render.readNativeImage
import net.ccbluex.liquidbounce.utils.render.registerTexture
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

@Suppress("TooManyFunctions")
object Appearance : ToggleableValueGroup(ModuleAmnesia, "Appearance", false) {

    private const val NAME_MC_SKIN_URL = "https://s.namemc.com/i/%s.png"

    private val nameMcSkinIdPattern = Regex("""(?:^|/)([0-9a-fA-F]{8,64})(?:\.png)?(?:$|[?#])""")

    private val playerValue = player("Player", "")
    private val nameMcSkinValue = text("NameMCSkin", "")

    private val loadingSkinNames = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var cachedSkin = SkinCache("", null, null)

    internal val spoofName: String?
        get() = playerValue.get().trim().takeIf { running && it.isNotEmpty() }

    private val nameMcSkinId: String?
        get() = parseNameMcSkinId(nameMcSkinValue.get())

    internal fun displayName(original: Component): Component? {
        val name = spoofName ?: return null

        findOnlineDisplayName(name)?.let { return it }
        return name.asPlainText(original.style)
    }

    internal fun skin(): PlayerSkin? {
        val name = spoofName ?: return null
        val skinId = nameMcSkinId

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

    internal fun hasSpoofedAppearance() = spoofName != null

    internal fun parseNameMcSkinId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        return nameMcSkinIdPattern.find(trimmed)?.groupValues?.get(1)?.lowercase()
    }

    private fun findOnlineDisplayName(name: String): Component? {
        val playerInfo = findOnlinePlayerInfo(name)
        playerInfo?.tabListDisplayName?.let { return it }
        playerInfo?.team?.let { return it.getFormattedName(name.asPlainText()) }

        val onlinePlayer = mc.level?.players()
            ?.firstOrNull { it.gameProfile.name.equals(name, ignoreCase = true) }
            ?: return null
        val styledName = name.asPlainText(onlinePlayer.name.style)

        return onlinePlayer.team?.getFormattedName(styledName) ?: styledName
    }

    private fun findOnlineSkin(name: String): PlayerSkin? {
        return findOnlinePlayerInfo(name)?.skin
    }

    private fun findOnlinePlayerInfo(name: String): PlayerInfo? {
        return mc.connection?.onlinePlayers?.firstOrNull {
            it.profile.name.equals(name, ignoreCase = true)
        }
    }

    private fun requestSkinLookup(name: String, nameMcSkinId: String?) {
        val key = skinCacheKey(name, nameMcSkinId)
        if (!loadingSkinNames.add(key)) {
            return
        }

        ioScope.launch {
            try {
                cachedSkin = SkinCache(name, nameMcSkinId, loadSkinSupplier(name, nameMcSkinId))
            } finally {
                loadingSkinNames.remove(key)
            }
        }
    }

    private suspend fun loadSkinSupplier(name: String, nameMcSkinId: String?): Supplier<PlayerSkin> {
        val profile = resolveSkinProfile(name)

        loadDownloadedSkinSupplier(profile, nameMcSkinId)?.let { return it }
        return PlayerInfo.createSkinLookup(profile.gameProfile)
    }

    private suspend fun resolveSkinProfile(name: String): ResolvedSkinProfile = withContext(Dispatchers.IO) {
        val uuid = GameProfileRepository.Default.fetchUuidByUsername(name)
            ?: generateOfflinePlayerUuid(name)
        val profile = mc.services.sessionService.fetchProfile(uuid, false)
        val gameProfile = profile?.profile ?: GameProfile(uuid, name)
        val skinTexture = profile?.let {
            mc.services.sessionService.unpackTextures(mc.services.sessionService.getPackedTextures(it.profile)).skin
        }

        ResolvedSkinProfile(
            gameProfile = gameProfile,
            skinUrl = skinTexture?.url,
            model = skinTexture?.getMetadata("model").toPlayerModelType(),
        )
    }

    private suspend fun loadDownloadedSkinSupplier(
        profile: ResolvedSkinProfile,
        nameMcSkinId: String?,
    ): Supplier<PlayerSkin>? {
        val url = nameMcSkinId?.let { NAME_MC_SKIN_URL.format(it) } ?: profile.skinUrl ?: return null

        return runCatching {
            val identifier = LiquidBounce.identifier("amnesia/appearance/skin-${Integer.toHexString(url.hashCode())}")
            val nativeImage = withContext(Dispatchers.IO) {
                HttpClient.request(url, HttpMethod.GET).use { response ->
                    check(response.body.contentLength() != 0L) { "Empty skin response" }
                    response.body.source().readNativeImage()
                }
            }

            withContext(Dispatchers.Minecraft) {
                nativeImage.registerTexture(identifier)
            }

            Supplier {
                PlayerSkin(
                    RegisteredSkinTexture(identifier),
                    null,
                    null,
                    profile.model,
                    false,
                )
            }
        }.getOrNull()
    }

    private fun skinCacheKey(name: String, nameMcSkinId: String?) =
        "${name.lowercase()}|${nameMcSkinId.orEmpty()}"

    private fun String?.toPlayerModelType() = if (this == PlayerModelType.SLIM.serializedName) {
        PlayerModelType.SLIM
    } else {
        PlayerModelType.WIDE
    }

    private data class SkinCache(
        val name: String,
        val nameMcSkinId: String?,
        val supplier: Supplier<PlayerSkin>?,
    ) {
        fun matches(otherName: String, otherNameMcSkinId: String?) =
            name.equals(otherName, ignoreCase = true) && nameMcSkinId == otherNameMcSkinId
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
}
