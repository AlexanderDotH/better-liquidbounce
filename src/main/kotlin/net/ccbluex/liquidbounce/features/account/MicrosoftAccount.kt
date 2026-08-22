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

package net.ccbluex.liquidbounce.features.account

import com.google.gson.JsonObject
import com.mojang.authlib.GameProfile
import net.ccbluex.liquidbounce.config.gson.util.obj
import net.ccbluex.liquidbounce.config.gson.util.string
import net.raphimc.minecraftauth.java.JavaAuthManager
import net.raphimc.minecraftauth.msa.data.MsaConstants
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig
import net.raphimc.minecraftauth.msa.model.MsaCredentials
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode
import net.raphimc.minecraftauth.msa.service.impl.CredentialsMsaAuthService
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService
import net.raphimc.minecraftauth.msa.service.impl.ExternalBrowserMsaAuthService
import java.util.function.Consumer

/**
 * A premium account authenticated through a Microsoft account.
 *
 * Authentication is delegated to [MinecraftAuth](https://github.com/CCBlueX/minecraft-auth-java),
 * which implements the full Microsoft -> Xbox Live -> XSTS -> Minecraft token exchange. Every
 * `buildFrom*` blocks the calling thread until the sign-in completes, so none of them may be called
 * on the render thread.
 */
class MicrosoftAccount internal constructor(
    private var authManager: JavaAuthManager?
) : MinecraftAccount(AccountService.MICROSOFT) {

    private var serializedAuthManager: JsonObject? = null
    private var legacyRefreshToken: String? = null
    private var legacyAuthMethod: String? = null

    /**
     * Used for JSON deserialize.
     */
    @Suppress("unused")
    constructor() : this(null)

    private fun requireAuthManager(): JavaAuthManager {
        authManager?.let { return it }

        serializedAuthManager?.let { serialized ->
            val restored = JavaAuthManager.fromJson(minecraftAuthHttpClient, serialized)
            authManager = restored
            serializedAuthManager = null
            return restored
        }

        val refreshToken = checkNotNull(legacyRefreshToken) { "Microsoft account has not been signed in" }
        val migrated = createAuthManagerFromRefreshToken(
            refreshToken,
            legacyApplicationConfig(legacyAuthMethod),
        )
        authManager = migrated
        legacyRefreshToken = null
        legacyAuthMethod = null
        return migrated
    }

    override fun refresh() {
        val manager = requireAuthManager()

        manager.minecraftToken.refresh()
        val minecraftProfile = manager.minecraftProfile.refresh()

        username = minecraftProfile.name
        profile = GameProfile(minecraftProfile.id, minecraftProfile.name)
    }

    override fun acquireAccessToken() = requireAuthManager().minecraftToken.getUpToDate().token

    override fun shouldRefreshBeforeLogin() = profile?.id == null || authManager == null

    /**
     * The full [JavaAuthManager] state (MSA refresh token and all cached Xbox/Minecraft tokens) is
     * embedded so that the session can be restored without signing in again.
     */
    override fun toRawJson(json: JsonObject) = json.run {
        writeProfile()
        authManager?.let {
            add("authManager", JavaAuthManager.toJson(it))
        } ?: serializedAuthManager?.let {
            add("authManager", it.deepCopy())
        } ?: run {
            addProperty("refreshToken", checkNotNull(legacyRefreshToken))
            addProperty("authMethod", legacyAuthMethod ?: LEGACY_DEFAULT_AUTH_METHOD)
        }
    }

    override fun fromRawJson(json: JsonObject) = json.run {
        readProfile()

        val authManagerJson = obj("authManager")
        if (authManagerJson != null) {
            serializedAuthManager = authManagerJson.deepCopy()
            legacyRefreshToken = null
            legacyAuthMethod = null
            return@run
        }

        legacyRefreshToken = string("refreshToken")
            ?: throw IllegalArgumentException("This Microsoft account has no reusable credentials")
        legacyAuthMethod = string("authMethod") ?: LEGACY_DEFAULT_AUTH_METHOD
    }

    companion object {

        /**
         * The official Minecraft (Java Edition) launcher application.
         */
        val JAVA_APPLICATION_CONFIG: MsaApplicationConfig =
            MsaApplicationConfig(MsaConstants.JAVA_TITLE_ID, MsaConstants.SCOPE_TITLE_AUTH)

        private const val DEFAULT_TIMEOUT_MS = 300_000
        private const val LEGACY_DEFAULT_AUTH_METHOD = "MINECRAFT_PC"

        private fun legacyApplicationConfig(authMethod: String?): MsaApplicationConfig = when (authMethod) {
            "MINECRAFT_NINTENDO_SWITCH" -> MsaApplicationConfig(
                "00000000441cc96b",
                "service::user.auth.xboxlive.com::MBI_SSL",
            ).withRedirectUri("https://login.live.com/oauth20_desktop.srf")
            "LIQUIDBOUNCE", "AZURE_APP" -> MsaApplicationConfig(
                "0add8caf-2cc6-4546-b798-c3d171217dd9",
                "XboxLive.signin offline_access",
            ).withRedirectUri("http://localhost")
            else -> MsaApplicationConfig(
                "00000000402B5328",
                "service::user.auth.xboxlive.com::MBI_SSL",
            ).withRedirectUri("https://login.live.com/oauth20_desktop.srf")
        }

        /**
         * Signs in through a browser the caller supplies. Preferred over the other flows, as it supports
         * 2FA, passkeys and everything else Microsoft offers.
         *
         * [onOpen] has to display [ExternalBrowserMsaAuthService.getAuthenticationUrl] and report the
         * URLs it navigates to back to the service; [onClose] runs once the sign-in has finished, failed
         * or timed out.
         */
        fun buildFromWebView(
            onOpen: (ExternalBrowserMsaAuthService) -> Unit,
            onClose: (ExternalBrowserMsaAuthService) -> Unit,
            applicationConfig: MsaApplicationConfig = JAVA_APPLICATION_CONFIG,
            timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        ): MicrosoftAccount = build(applicationConfig) {
            it.login { httpClient, config ->
                ExternalBrowserMsaAuthService(httpClient, config, onOpen, onClose, timeoutMs)
            }
        }

        /**
         * [onDeviceCode] is invoked once with the code the user has to enter at the returned verification
         * URL; this blocks until the user completes the sign-in, the code expires, or [timeoutMs] elapses.
         */
        fun buildFromDeviceCode(
            onDeviceCode: Consumer<MsaDeviceCode>,
            applicationConfig: MsaApplicationConfig = JAVA_APPLICATION_CONFIG,
            timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        ): MicrosoftAccount = build(applicationConfig) {
            it.login(
                { httpClient, config, callback -> DeviceCodeMsaAuthService(httpClient, config, callback, timeoutMs) },
                onDeviceCode,
            )
        }

        /**
         * Does not support accounts with two-factor authentication enabled; use [buildFromWebView] or
         * [buildFromDeviceCode] for those.
         */
        fun buildFromCredentials(
            email: String,
            password: String,
            applicationConfig: MsaApplicationConfig = JAVA_APPLICATION_CONFIG,
        ): MicrosoftAccount = build(applicationConfig) {
            it.login(
                { httpClient, config, credentials -> CredentialsMsaAuthService(httpClient, config, credentials) },
                MsaCredentials(email, password),
            )
        }

        /**
         * [applicationConfig] must match the one the refresh token was originally issued for.
         */
        fun buildFromRefreshToken(
            refreshToken: String,
            applicationConfig: MsaApplicationConfig = JAVA_APPLICATION_CONFIG,
        ): MicrosoftAccount = build(applicationConfig) { it.login(refreshToken) }

        private fun createAuthManagerFromRefreshToken(
            refreshToken: String,
            applicationConfig: MsaApplicationConfig,
        ): JavaAuthManager = JavaAuthManager.create(minecraftAuthHttpClient)
            .msaApplicationConfig(applicationConfig)
            .login(refreshToken)

        private fun build(
            applicationConfig: MsaApplicationConfig,
            login: (JavaAuthManager.Builder) -> JavaAuthManager,
        ): MicrosoftAccount {
            val builder = JavaAuthManager.create(minecraftAuthHttpClient).msaApplicationConfig(applicationConfig)
            return MicrosoftAccount(login(builder)).apply { refresh() }
        }

    }

}
