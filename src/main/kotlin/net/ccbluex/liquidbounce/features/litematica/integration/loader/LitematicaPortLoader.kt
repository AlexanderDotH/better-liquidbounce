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
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaBridgeResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCapabilities
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaIntegrationVersions
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPortFactory
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

@Suppress("TooManyFunctions")
internal class ReflectiveLitematicaPortLoader(
    private val versionLookup: LitematicaModVersionLookup = FabricLitematicaModVersionLookup,
    private val classLookup: LitematicaClassLookup = defaultClassLookup(),
    private val requiredExternalClasses: List<String> = REQUIRED_EXTERNAL_CLASSES,
    private val bridgeFactoryClassName: String = LitematicaPortLoader.BRIDGE_FACTORY_CLASS,
) {
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
        return createBridge(versions)
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

    private fun createBridge(versions: LitematicaIntegrationVersions): LitematicaPortLoadResult = try {
        val factoryClass = classLookup.resolve(bridgeFactoryClassName, true)
        val factory = instantiateFactory(factoryClass)
        when (val bridge = factory.create()) {
            is LitematicaBridgeResult.Ready -> ready(versions, bridge.port)
            is LitematicaBridgeResult.Unsupported -> unavailable(
                versions,
                LitematicaUnavailableReason.CAPABILITY_MISMATCH,
                bridge.detail,
                bridge.capabilities,
            )
        }
    } catch (failure: ClassNotFoundException) {
        unavailable(
            versions,
            LitematicaUnavailableReason.MISSING_CLASS,
            classDetail(bridgeFactoryClassName, failure),
        )
    } catch (failure: ReflectiveOperationException) {
        unavailable(
            versions,
            LitematicaUnavailableReason.INVALID_BRIDGE_FACTORY,
            failureDetail(failure),
        )
    } catch (failure: LinkageError) {
        unavailable(versions, LitematicaUnavailableReason.BRIDGE_FAILURE, failureDetail(failure))
    } catch (failure: RuntimeException) {
        unavailable(versions, LitematicaUnavailableReason.BRIDGE_FAILURE, failureDetail(failure))
    }

    private fun instantiateFactory(factoryClass: Class<*>): LitematicaPortFactory {
        if (!LitematicaPortFactory::class.java.isAssignableFrom(factoryClass)) {
            throw ReflectiveOperationException(
                "$bridgeFactoryClassName must implement ${LitematicaPortFactory::class.java.name}",
            )
        }
        return factoryClass.getDeclaredConstructor().newInstance() as LitematicaPortFactory
    }

    private fun ready(
        versions: LitematicaIntegrationVersions,
        port: LitematicaPort,
    ): LitematicaPortLoadResult {
        val missingCapabilities = port.capabilities.missingRequired()
        if (missingCapabilities.isNotEmpty()) {
            port.close()
            return unavailable(
                versions,
                LitematicaUnavailableReason.CAPABILITY_MISMATCH,
                "Bridge is missing required capabilities: " +
                    missingCapabilities.sortedBy { it.name }.joinToString(),
            )
        }
        if (port.versions != versions) {
            port.close()
            return unavailable(
                versions,
                LitematicaUnavailableReason.VERSION_MISMATCH,
                "Bridge reported ${port.versions}, but Fabric reported $versions",
            )
        }
        return LitematicaPortLoadResult.Ready(
            port = port,
            availability = LitematicaAvailability.Available(versions, port.capabilities),
        )
    }

    private fun unavailable(
        versions: LitematicaIntegrationVersions,
        reason: LitematicaUnavailableReason,
        detail: String,
        capabilities: LitematicaCapabilities? = null,
    ) = LitematicaPortLoadResult.Unavailable(
        LitematicaAvailability.Unavailable(versions, reason, detail, capabilities),
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
