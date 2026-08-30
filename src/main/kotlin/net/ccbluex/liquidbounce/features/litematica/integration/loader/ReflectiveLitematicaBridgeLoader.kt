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
import java.lang.reflect.InvocationTargetException

internal class ReflectiveLitematicaBridgeLoader(
    private val classLookup: LitematicaClassLookup,
    private val bridgeFactoryClassName: String,
) {
    fun create(versions: LitematicaIntegrationVersions): LitematicaPortLoadResult = try {
        val factory = instantiateFactory(classLookup.resolve(bridgeFactoryClassName, true))
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
        unavailable(versions, LitematicaUnavailableReason.MISSING_CLASS, classDetail(failure))
    } catch (failure: ReflectiveOperationException) {
        unavailable(versions, LitematicaUnavailableReason.INVALID_BRIDGE_FACTORY, failureDetail(failure))
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

    private fun ready(versions: LitematicaIntegrationVersions, port: LitematicaPort): LitematicaPortLoadResult {
        val missingCapabilities = port.capabilities.missingRequired()
        if (missingCapabilities.isNotEmpty()) {
            port.close()
            return unavailable(
                versions,
                LitematicaUnavailableReason.CAPABILITY_MISMATCH,
                "Bridge is missing required capabilities: ${missingCapabilities.sortedBy { it.name }.joinToString()}",
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
            port,
            LitematicaAvailability.Available(versions, port.capabilities),
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

    private fun classDetail(failure: Throwable) =
        "Required class $bridgeFactoryClassName is unavailable: ${failureDetail(failure)}"

    private fun failureDetail(failure: Throwable): String {
        val cause = (failure as? InvocationTargetException)?.targetException ?: failure
        return cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName
    }
}
