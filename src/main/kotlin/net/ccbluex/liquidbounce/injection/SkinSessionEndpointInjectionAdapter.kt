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
package net.ccbluex.liquidbounce.injection

import net.ccbluex.liquidbounce.features.module.modules.render.skinchanger.SkinSessionEndpointBridge
import net.ccbluex.liquidbounce.features.module.modules.render.skinchanger.SkinSessionEndpointHook
import net.ccbluex.liquidbounce.injection.mixins.authlib.MixinYggdrasilMinecraftSessionServiceAccessor

object SkinSessionEndpointInjectionAdapter {

    @JvmStatic
    fun install() = SkinSessionEndpointBridge.install(SkinSessionEndpointHook(::skinSessionBaseUrl))
}

internal fun skinSessionBaseUrl(sessionService: Any): String? =
    (sessionService as? MixinYggdrasilMinecraftSessionServiceAccessor)?.baseUrl
