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
package net.ccbluex.liquidbounce.features.litematica.integration.loader

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaAvailability
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaIntegrationVersions
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaUnavailableReason
import net.fabricmc.loader.api.FabricLoader
import java.lang.reflect.InvocationTargetException

fun interface LitematicaModVersionLookup {
    fun version(modId: String): String?
}

fun interface LitematicaClassLookup {
    fun resolve(className: String, initialize: Boolean): Class<*>
}

sealed interface LitematicaPortLoadResult {
    val availability: LitematicaAvailability

    data class Ready(
        val port: LitematicaPort,
        override val availability: LitematicaAvailability.Available,
    ) : LitematicaPortLoadResult

    data class Unavailable(
        override val availability: LitematicaAvailability.Unavailable,
    ) : LitematicaPortLoadResult
}

object LitematicaPortLoader {
    const val EXPECTED_LITEMATICA_VERSION = "0.28.4"
    const val EXPECTED_MALILIB_VERSION = "0.29.3"
    const val BRIDGE_FACTORY_CLASS =
        "net.ccbluex.liquidbounce.features.litematica.integration.litematica262.Litematica262BridgeFactory"

    @JvmStatic
    fun load(): LitematicaPortLoadResult = ReflectiveLitematicaPortLoader().load()
}

internal class ReflectiveLitematicaPortLoader(
    private val versionLookup: LitematicaModVersionLookup = FabricLitematicaModVersionLookup,
    private val classLookup: LitematicaClassLookup = defaultClassLookup(),
    private val requiredExternalClasses: List<String> = REQUIRED_EXTERNAL_CLASSES,
    private val bridgeFactoryClassName: String = LitematicaPortLoader.BRIDGE_FACTORY_CLASS,
) {
    private val bridgeLoader = ReflectiveLitematicaBridgeLoader(classLookup, bridgeFactoryClassName)

    fun load(): LitematicaPortLoadResult {
        val versions = try {
            installedVersions()
        } catch (failure: LinkageError) {
            return lookupFailure(failure)
        } catch (failure: RuntimeException) {
            return lookupFailure(failure)
        }
        missingMod(versions)?.let { return unavailable(versions, LitematicaUnavailableReason.MISSING_MOD, it) }
        versionMismatch(versions)?.let {
            return unavailable(versions, LitematicaUnavailableReason.VERSION_MISMATCH, it)
        }
        resolveExternalClasses(versions)?.let { return it }
        return bridgeLoader.create(versions)
    }

    private fun installedVersions() = LitematicaIntegrationVersions(
        litematica = versionLookup.version(LITEMATICA_MOD_ID),
        malilib = versionLookup.version(MALILIB_MOD_ID),
    )

    private fun missingMod(versions: LitematicaIntegrationVersions): String? {
        val missing = buildList {
            if (versions.litematica == null) add(LITEMATICA_MOD_ID)
            if (versions.malilib == null) add(MALILIB_MOD_ID)
        }
        return missing.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "Missing optional mod(s): ",
        )
    }

    private fun versionMismatch(versions: LitematicaIntegrationVersions): String? {
        val mismatches = buildList {
            if (versions.litematica != LitematicaPortLoader.EXPECTED_LITEMATICA_VERSION) {
                add("litematica ${versions.litematica}, expected ${LitematicaPortLoader.EXPECTED_LITEMATICA_VERSION}")
            }
            if (versions.malilib != LitematicaPortLoader.EXPECTED_MALILIB_VERSION) {
                add("malilib ${versions.malilib}, expected ${LitematicaPortLoader.EXPECTED_MALILIB_VERSION}")
            }
        }
        return mismatches.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "Incompatible optional mod version(s): ",
        )
    }

    private fun resolveExternalClasses(
        versions: LitematicaIntegrationVersions,
    ): LitematicaPortLoadResult.Unavailable? {
        for (className in requiredExternalClasses) {
            try {
                classLookup.resolve(className, false)
            } catch (failure: ClassNotFoundException) {
                return unavailable(versions, LitematicaUnavailableReason.MISSING_CLASS, classDetail(className, failure))
            } catch (failure: LinkageError) {
                return unavailable(versions, LitematicaUnavailableReason.MISSING_CLASS, classDetail(className, failure))
            } catch (failure: RuntimeException) {
                return unavailable(versions, LitematicaUnavailableReason.MISSING_CLASS, classDetail(className, failure))
            }
        }
        return null
    }

    private fun unavailable(
        versions: LitematicaIntegrationVersions,
        reason: LitematicaUnavailableReason,
        detail: String,
    ) = LitematicaPortLoadResult.Unavailable(
        LitematicaAvailability.Unavailable(versions, reason, detail),
    )

    private fun lookupFailure(failure: Throwable) = unavailable(
        versions = LitematicaIntegrationVersions(null, null),
        reason = LitematicaUnavailableReason.BRIDGE_FAILURE,
        detail = "Unable to inspect optional mod versions: ${failureDetail(failure)}",
    )

    private fun classDetail(className: String, failure: Throwable) =
        "Required class $className is unavailable: ${failureDetail(failure)}"

    private fun failureDetail(failure: Throwable): String {
        val cause = (failure as? InvocationTargetException)?.targetException ?: failure
        return cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName
    }

    private companion object {
        const val LITEMATICA_MOD_ID = "litematica"
        const val MALILIB_MOD_ID = "malilib"

        val REQUIRED_EXTERNAL_CLASSES = listOf(
            "fi.dy.masa.litematica.Litematica",
            "fi.dy.masa.litematica.data.DataManager",
            "fi.dy.masa.litematica.util.EasyPlaceUtils",
            "fi.dy.masa.malilib.interoperation.BlockPlacementPositionHandler",
        )

        fun defaultClassLookup(): LitematicaClassLookup {
            val loader = LitematicaPortLoader::class.java.classLoader
            return LitematicaClassLookup { className, initialize ->
                Class.forName(className, initialize, loader)
            }
        }
    }
}

private object FabricLitematicaModVersionLookup : LitematicaModVersionLookup {
    override fun version(modId: String): String? = FabricLoader.getInstance()
        .getModContainer(modId)
        .map { it.metadata.version.friendlyString }
        .orElse(null)
}
