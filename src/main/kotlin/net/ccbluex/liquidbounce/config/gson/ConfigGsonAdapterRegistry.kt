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
package net.ccbluex.liquidbounce.config.gson

import com.google.gson.GsonBuilder

object ConfigGsonAdapterRegistry {

    private val registry = MutableConfigGsonAdapterRegistry()

    fun install(
        scope: ConfigGsonAdapterScope = ConfigGsonAdapterScope.COMMON,
        registration: GsonBuilder.() -> Unit,
    ) = registry.install(scope, registration)

    internal fun applyTo(
        builder: GsonBuilder,
        scope: ConfigGsonAdapterScope = ConfigGsonAdapterScope.COMMON,
    ): GsonBuilder = registry.applyTo(builder, scope)
}

internal class MutableConfigGsonAdapterRegistry {
    private val registrations = ConfigGsonAdapterScope.entries.associateWith {
        mutableListOf<GsonBuilder.() -> Unit>()
    }
    private var sealed = false

    @Synchronized
    fun install(
        scope: ConfigGsonAdapterScope = ConfigGsonAdapterScope.COMMON,
        registration: GsonBuilder.() -> Unit,
    ) {
        check(!sealed) { "Config Gson adapter registry is already in use" }
        registrations.getValue(scope) += registration
    }

    @Synchronized
    fun applyTo(
        builder: GsonBuilder,
        scope: ConfigGsonAdapterScope = ConfigGsonAdapterScope.COMMON,
    ): GsonBuilder {
        sealed = true
        registrations.getValue(scope).forEach { registration -> registration(builder) }
        return builder
    }
}

enum class ConfigGsonAdapterScope {
    COMMON,
    ACCESSIBLE_INTEROP,
}
