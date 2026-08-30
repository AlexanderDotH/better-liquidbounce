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
@file:OptIn(FlowPreview::class)

package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.api.core.renderScope
import net.ccbluex.liquidbounce.api.thirdparty.lookupUuidByName
import net.ccbluex.liquidbounce.api.thirdparty.mojang.model.ChangeSkinRequest
import net.ccbluex.liquidbounce.api.thirdparty.mojang.service.MinecraftServicesApi
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.SuspendHandlerBehavior
import net.ccbluex.liquidbounce.event.events.SessionEvent
import net.ccbluex.liquidbounce.event.suspendHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.skinchanger.SkinUploadAccess
import net.ccbluex.liquidbounce.features.module.modules.render.skinchanger.skinVariant
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.clientIdentifier
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.ccbluex.liquidbounce.utils.render.readNativeImage
import net.ccbluex.liquidbounce.utils.render.registerTexture
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.core.ClientAsset
import net.minecraft.core.UUIDUtil
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

object ModuleSkinChanger : ClientModule("SkinChanger", ModuleCategories.RENDER) {

    /** Force-overrides [AbstractClientPlayer.getSkin] when server player information is unreliable. */
    private val allowMixinAbstractClientPlayerEntity by boolean("ForceOverride", false)

    private val uploadSkin = boolean("UploadSkin", false)

    private val mode = choices("Mode", 0) {
        arrayOf(Mode.Online, Mode.File)
    }

    private val DEBOUNCE_DURATION = 3.seconds

    private val uploadSkinFlow = MutableSharedFlow<Unit>(replay = 0)

    init {
        ioScope.launch {
            uploadSkinFlow.debounce(DEBOUNCE_DURATION).filter { canUploadSkin() }.collectLatest {
                logger.info("Uploading skin...")
                mode.activeMode.uploadSkin()
            }
        }

        ioScope.launch {
            combine(uploadSkin.asStateFlow(), mode.asStateFlow()) { skin, mode ->
                if (skin) {
                    triggerUpload()
                }
            }.collect()
        }
    }

    private suspend fun waitUntilInGame() {
        while (!inGame) {
            delay(1.seconds)
        }
    }

    private inline fun <T> Flow<T>.debounceUntilInGame(crossinline action: suspend (T) -> Unit) {
        renderScope.launch {
            this@debounceUntilInGame.debounce(DEBOUNCE_DURATION).collectLatest {
                waitUntilInGame()
                try {
                    action(it)
                } catch (e: Exception) {
                    if (this@ModuleSkinChanger.running) {
                        chat("Unable to load custom skin because: ${e.message} (${e.javaClass.simpleName})")
                    }
                    logger.error("Unable to load custom skin", e)
                }
            }
        }
    }

    private sealed class Mode(name: String) : net.ccbluex.liquidbounce.config.types.group.Mode(name) {
        final override val parent: ModeValueGroup<*>
            get() = mode

        abstract val skinTextures: Supplier<PlayerSkin>?

        abstract suspend fun uploadSkin()

        object Online : Mode("Online") {
            private val username = text("Username", "LiquidBounce")

            init {
                username.asStateFlow().debounceUntilInGame { username ->
                    skinTextures = textureSupplier(username)

                    triggerUpload()
                }
            }

            override var skinTextures: Supplier<PlayerSkin>? = null

            private suspend fun textureSupplier(username: String): Supplier<PlayerSkin> {
                val profile = withContext(Dispatchers.IO) {
                    val uuid = lookupUuidByName(username) ?: UUIDUtil.createOfflinePlayerUUID(username)
                    mc.services.sessionService.fetchProfile(uuid, false)?.profile
                        ?: GameProfile(uuid, username)
                }

                return PlayerInfo.createSkinLookup(profile)
            }

            override suspend fun uploadSkin() {
                val uuid = lookupUuidByName(username.get()) ?: return

                val profile = withContext(Dispatchers.IO) {
                    mc.services.sessionService.fetchProfile(uuid, false)
                } ?: return

                val texture = mc.services.sessionService.unpackTextures(
                    mc.services.sessionService.getPackedTextures(profile.profile)
                )
                val skinTexture = texture.skin ?: return
                val variant = if (skinTexture.getMetadata("model") == "slim") {
                    PlayerModelType.SLIM
                } else {
                    PlayerModelType.WIDE
                }

                request {
                    changeSkin(ChangeSkinRequest(variant.skinVariant, skinTexture.url))
                }
            }
        }

        object File : Mode("File"), ClientAsset.Texture {
            private val image = file("Image", supportedExtensions = setOf("png"))

            private val skinType by enumChoice("Model", ModelChoice.WIDE)

            private val identifier = clientIdentifier("skin-changer-from-file")

            override fun id() = identifier

            override fun texturePath() = identifier

            private enum class ModelChoice(
                override val tag: String,
                val type: PlayerModelType,
            ) : Tagged {
                SLIM("Slim", PlayerModelType.SLIM),
                WIDE("Default", PlayerModelType.WIDE),
            }

            override val skinTextures = Supplier {
                PlayerSkin(
                    this,
                    null,
                    null,
                    skinType.type,
                    false,
                )
            }

            init {
                image.asStateFlow().filter { it.isFile }.debounceUntilInGame { file ->
                    val nativeImage = withContext(Dispatchers.IO) {
                        file.readNativeImage()
                    }

                    withContext(Dispatchers.Minecraft) {
                        nativeImage.registerTexture(identifier)
                    }

                    triggerUpload()
                }
            }

            override suspend fun uploadSkin() {
                val file = image.get()
                if (!file.isFile) {
                    return
                }

                request {
                    uploadSkin(
                        skinType.type.skinVariant.toRequestBody(HttpClient.MediaTypes.TEXT_PLAIN),
                        MultipartBody.Part.createFormData(
                            name = "file",
                            filename = "skin.png",
                            body = file.asRequestBody(HttpClient.MediaTypes.IMAGE_PNG)
                        )
                    )
                }
            }
        }
    }

    val skinTextures: Supplier<PlayerSkin>? get() = mode.activeMode.skinTextures

    @JvmStatic
    fun shouldApplyChanges(): Boolean =
        running && allowMixinAbstractClientPlayerEntity

    @Suppress("unused")
    private val sessionHandler = suspendHandler<SessionEvent>(behavior = SuspendHandlerBehavior.CancelPrevious) {
        triggerUpload()
    }

    override suspend fun enabledEffect() {
        triggerUpload()
    }

    private suspend fun triggerUpload() {
        uploadSkinFlow.emit(Unit)
    }

    private inline fun request(block: MinecraftServicesApi.() -> Unit) = SkinUploadAccess.request(block)

    private fun canUploadSkin(): Boolean = SkinUploadAccess.canUpload(running, uploadSkin.get())
}
