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
package net.ccbluex.liquidbounce.features.litematica.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.core.BlockPos

enum class LitematicaTargetStyle {
    PLACE,
    BREAK,
    FLUID,
    BLOCKED,
    PENDING,
}

data class LitematicaTargetColors(
    val fill: Color4b,
    val outline: Color4b,
)

object LitematicaTargetPalette {
    private val colorsByStyle = mapOf(
        LitematicaTargetStyle.PLACE to targetColors(Color4b.GREEN, 55),
        LitematicaTargetStyle.BREAK to targetColors(Color4b.RED, 55),
        LitematicaTargetStyle.FLUID to targetColors(Color4b.BLUE, 65),
        LitematicaTargetStyle.BLOCKED to targetColors(Color4b.YELLOW, 55),
        LitematicaTargetStyle.PENDING to targetColors(Color4b.GRAY, 55),
    )

    fun colorsFor(style: LitematicaTargetStyle): LitematicaTargetColors = colorsByStyle.getValue(style)

    private fun targetColors(color: Color4b, fillAlpha: Int) = LitematicaTargetColors(
        fill = color.alpha(fillAlpha),
        outline = color.alpha(210),
    )
}

data class LitematicaRenderTarget(
    val position: BlockPos,
    val style: LitematicaTargetStyle,
    val detail: String? = null,
)

data class LitematicaPlacementCounts(
    val correct: Int = 0,
    val missing: Int = 0,
    val wrong: Int = 0,
    val extra: Int = 0,
    val pending: Int = 0,
) {
    init {
        require(listOf(correct, missing, wrong, extra, pending).all { it >= 0 }) {
            "Placement counts must not be negative"
        }
    }
}

data class LitematicaVerifierTotals(
    val correct: Int,
    val missing: Int,
    val wrong: Int,
    val extra: Int,
) {
    init {
        require(listOf(correct, missing, wrong, extra).all { it >= 0 }) {
            "Verifier totals must not be negative"
        }
    }
}

data class LitematicaHudSnapshot(
    val placementName: String?,
    val activationMode: String,
    val counts: LitematicaPlacementCounts,
    val currentTarget: BlockPos? = null,
    val missingMaterial: String? = null,
    val pauseReason: String? = null,
    val retryCount: Int,
    val verifierTotals: LitematicaVerifierTotals? = null,
) {
    init {
        require(activationMode.isNotBlank()) { "Activation mode must not be blank" }
        require(retryCount >= 0) { "Retry count must not be negative" }
    }
}

data class LitematicaRenderSnapshot(
    val targets: List<LitematicaRenderTarget> = emptyList(),
    val hud: LitematicaHudSnapshot? = null,
) {
    companion object {
        val EMPTY = LitematicaRenderSnapshot()
    }
}

enum class LitematicaHudTone {
    TITLE,
    NORMAL,
    WARNING,
    ERROR,
    MUTED,
}

data class LitematicaHudLine(
    val text: String,
    val tone: LitematicaHudTone = LitematicaHudTone.NORMAL,
)

data class LitematicaHudPresentation(val lines: List<LitematicaHudLine>)

object LitematicaHudPresenter {
    fun present(snapshot: LitematicaHudSnapshot): LitematicaHudPresentation {
        val lines = mutableListOf(
            title(snapshot),
            localCounts(snapshot.counts),
        )
        snapshot.currentTarget?.let { lines += LitematicaHudLine("Target: ${it.x}, ${it.y}, ${it.z}") }
        snapshot.missingMaterial?.let { lines += LitematicaHudLine("Missing: $it", LitematicaHudTone.WARNING) }
        snapshot.pauseReason?.let { lines += LitematicaHudLine("Paused: $it", LitematicaHudTone.ERROR) }
        lines += retryLine(snapshot)
        snapshot.verifierTotals?.let { lines += verifierLine(it) }
        return LitematicaHudPresentation(lines.toList())
    }

    private fun title(snapshot: LitematicaHudSnapshot): LitematicaHudLine {
        val placementName = snapshot.placementName?.takeIf(String::isNotBlank) ?: "No placement"
        return LitematicaHudLine(
            "Litematica | $placementName | ${snapshot.activationMode}",
            LitematicaHudTone.TITLE,
        )
    }

    private fun localCounts(counts: LitematicaPlacementCounts) = LitematicaHudLine(
        "Local: ${counts.correct} correct | ${counts.missing} missing | ${counts.wrong} wrong | " +
            "${counts.extra} extra | ${counts.pending} pending",
    )

    private fun retryLine(snapshot: LitematicaHudSnapshot) = LitematicaHudLine(
        "Retries: ${snapshot.retryCount}",
        if (snapshot.retryCount > 0) LitematicaHudTone.WARNING else LitematicaHudTone.NORMAL,
    )

    private fun verifierLine(totals: LitematicaVerifierTotals) = LitematicaHudLine(
        "Verifier: ${totals.correct} correct | ${totals.missing} missing | ${totals.wrong} wrong | " +
            "${totals.extra} extra",
        LitematicaHudTone.MUTED,
    )
}
