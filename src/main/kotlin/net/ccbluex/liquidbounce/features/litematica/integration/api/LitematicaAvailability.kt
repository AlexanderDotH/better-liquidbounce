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
package net.ccbluex.liquidbounce.features.litematica.integration.api

data class LitematicaIntegrationVersions(
    val litematica: String?,
    val malilib: String?,
)

enum class LitematicaCapability {
    PLACEMENT_METADATA,
    LOCAL_SCAN,
    MATERIALS,
    RENDER_LAYER,
    EASY_PLACE_STATE,
    EASY_PLACE_ACTION,
    EASY_PLACE_SUPPRESSION,
    POSITION_PROVIDER,
    VERIFIER,
}

data class LitematicaCapabilities private constructor(
    val supported: Set<LitematicaCapability>,
) {
    fun missingRequired(): Set<LitematicaCapability> = REQUIRED - supported

    companion object {
        private val REQUIRED = setOf(
            LitematicaCapability.PLACEMENT_METADATA,
            LitematicaCapability.LOCAL_SCAN,
            LitematicaCapability.MATERIALS,
            LitematicaCapability.RENDER_LAYER,
            LitematicaCapability.EASY_PLACE_STATE,
            LitematicaCapability.EASY_PLACE_ACTION,
            LitematicaCapability.EASY_PLACE_SUPPRESSION,
            LitematicaCapability.POSITION_PROVIDER,
        )

        fun of(vararg supported: LitematicaCapability) = LitematicaCapabilities(supported.toSet())

        fun of(supported: Set<LitematicaCapability>) = LitematicaCapabilities(supported.toSet())

        fun required() = LitematicaCapabilities(REQUIRED)
    }
}

sealed interface LitematicaAvailability {
    val versions: LitematicaIntegrationVersions

    data class Available(
        override val versions: LitematicaIntegrationVersions,
        val capabilities: LitematicaCapabilities,
    ) : LitematicaAvailability

    data class Unavailable(
        override val versions: LitematicaIntegrationVersions,
        val reason: LitematicaUnavailableReason,
        val detail: String,
        val capabilities: LitematicaCapabilities? = null,
    ) : LitematicaAvailability
}

enum class LitematicaUnavailableReason {
    MISSING_MOD,
    VERSION_MISMATCH,
    MISSING_CLASS,
    INVALID_BRIDGE_FACTORY,
    CAPABILITY_MISMATCH,
    BRIDGE_FAILURE,
}
