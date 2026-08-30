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
@file:JvmName("SeedCrackerHudComponentKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.theme.component.components.seedcracker

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackerState
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerStatus
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedEta
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedPercent
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedRate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.seedCrackerTranslation

internal fun seedCrackerStatusLines(
    status: SeedCrackerStatus,
    chrome: SeedCrackerHudChrome,
): List<StatusLine> = buildList {
    add(StatusLine(
        text = "SeedCracker · ${stateLabel(status.state)}",
        color = chrome.titleColor.argb,
        role = SeedCrackerHudLineRole.TITLE,
        bold = true,
    ))
    val candidate = status.candidate.takeIf { status.state == CrackerState.CANDIDATE }
    addScopeLines(status, chrome, showProgress = candidate == null)
    addOutcomeLines(status, chrome, candidate)
}.take(MAX_LINES)

private fun MutableList<StatusLine>.addScopeLines(
    status: SeedCrackerStatus,
    chrome: SeedCrackerHudChrome,
    showProgress: Boolean,
) {
    when {
        status.scope.isOverworld -> addOverworldLines(status, chrome, showProgress)
        status.scope.isNether -> addNetherLines(status, chrome, showProgress)
    }
}

private fun MutableList<StatusLine>.addOverworldLines(
    status: SeedCrackerStatus,
    chrome: SeedCrackerHudChrome,
    showProgress: Boolean,
) {
    add(StatusLine(seedCrackerTranslation(
        "overlay.structures",
        status.acceptedStructureCount,
        status.pendingStructureCount,
    ).string, chrome.bodyColor.argb))
    status.structureProgress?.takeIf { showProgress }?.let { progress ->
        add(StatusLine(seedCrackerTranslation(
            "overlay.shipwreckProgress",
            progress.acceptedIndependentEvidence,
            progress.requiredIndependentEvidence,
        ).string, chrome.bodyColor.argb))
    }
}

private fun MutableList<StatusLine>.addNetherLines(
    status: SeedCrackerStatus,
    chrome: SeedCrackerHudChrome,
    showProgress: Boolean,
) {
    add(StatusLine(seedCrackerTranslation(
        "overlay.netherBedrock",
        status.acceptedNetherBedrockChunkCount,
        status.pendingNetherBedrockChunkCount,
    ).string, chrome.bodyColor.argb))
    status.netherSearchProgress?.takeIf { showProgress }?.let { progress ->
        add(StatusLine(seedCrackerTranslation(
            if (progress.paused) "overlay.netherProgressPaused" else "overlay.netherProgress",
            progress.formattedPercent(),
            progress.formattedRate(),
            progress.formattedEta(),
        ).string, chrome.bodyColor.argb))
    }
}

private fun MutableList<StatusLine>.addOutcomeLines(
    status: SeedCrackerStatus,
    chrome: SeedCrackerHudChrome,
    candidate: net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCandidate?,
) {
    if (candidate == null) {
        add(StatusLine(shortNextAction(status), chrome.actionColor.argb, role = SeedCrackerHudLineRole.ACTION))
        return
    }
    seedCrackerHudCandidateLinePlan(candidate).forEach { line ->
        add(StatusLine(
            text = seedCrackerTranslation(line.translationKey, *line.arguments.toTypedArray()).string,
            color = when (line.role) {
                SeedCrackerHudLineRole.RESULT -> chrome.actionColor.argb
                else -> chrome.accentColor.argb
            },
            role = line.role,
            bold = line.role == SeedCrackerHudLineRole.RESULT,
        ))
    }
}

private const val MAX_LINES = 4
