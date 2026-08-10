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
package net.ccbluex.liquidbounce.render.engine.esp

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import java.util.EnumMap

data class EspGlowStyle(
    val radius: Float,
    val softness: Float,
    val intensity: Float,
    val coreSize: Float,
    val opacity: Float,
) {
    companion object {
        val DEFAULT = EspGlowStyle(
            radius = 14f,
            softness = 1f,
            intensity = 1f,
            coreSize = 1.25f,
            opacity = 1f,
        )
    }
}

data class EspOutlineStyle(
    val thickness: Float,
    val opacity: Float,
) {
    companion object {
        val DEFAULT = EspOutlineStyle(thickness = 2f, opacity = 1f)
    }
}

class EspGlowStyleConfig(owner: ValueGroup) {
    private val radius by owner.float("Radius", EspGlowStyle.DEFAULT.radius, 4f..24f, "px")
    private val softness by owner.float("Softness", EspGlowStyle.DEFAULT.softness, 0.5f..1.5f)
    private val intensity by owner.float("Intensity", EspGlowStyle.DEFAULT.intensity, 0f..2f)
    private val coreSize by owner.float("CoreSize", EspGlowStyle.DEFAULT.coreSize, 0f..3f, "px")
    private val opacity by owner.int("Opacity", 100, 0..100, "%")

    val style: EspGlowStyle
        get() = EspGlowStyle(radius, softness, intensity, coreSize, opacity / 100f)
}

/** Gaussian halo without the compositor's additional bright outer core. */
class EspHaloStyleConfig(owner: ValueGroup) {
    private val radius by owner.float("Radius", EspGlowStyle.DEFAULT.radius, 4f..24f, "px")
    private val softness by owner.float("Softness", EspGlowStyle.DEFAULT.softness, 0.5f..1.5f)
    private val intensity by owner.float("Intensity", EspGlowStyle.DEFAULT.intensity, 0f..2f)
    private val opacity by owner.int("Opacity", 100, 0..100, "%")

    val style: EspGlowStyle
        get() = EspGlowStyle(radius, softness, intensity, coreSize = 0f, opacity / 100f)
}

class EspOutlineStyleConfig(owner: ValueGroup) {
    private val thickness by owner.float("Thickness", EspOutlineStyle.DEFAULT.thickness, 0.5f..4f, "px")
    private val opacity by owner.int("Opacity", 100, 0..100, "%")

    val style: EspOutlineStyle
        get() = EspOutlineStyle(thickness, opacity / 100f)
}

/**
 * Resolves repeated submissions from one logical source. Different module sources own different masks
 * and are never passed through this resolver together.
 */
object EspShaderStyleResolver {

    fun resolveGlow(vararg styles: EspGlowStyle?): EspGlowStyle {
        var resolved: EspGlowStyle? = null
        for (style in styles) {
            if (style == null) continue
            val current = resolved
            resolved = if (current == null) {
                style
            } else {
                EspGlowStyle(
                    radius = maxOf(current.radius, style.radius),
                    softness = maxOf(current.softness, style.softness),
                    intensity = maxOf(current.intensity, style.intensity),
                    coreSize = maxOf(current.coreSize, style.coreSize),
                    opacity = maxOf(current.opacity, style.opacity),
                )
            }
        }
        return resolved ?: EspGlowStyle.DEFAULT
    }

    fun resolveOutline(vararg styles: EspOutlineStyle?): EspOutlineStyle {
        var resolved: EspOutlineStyle? = null
        for (style in styles) {
            if (style == null) continue
            val current = resolved
            resolved = if (current == null) {
                style
            } else {
                EspOutlineStyle(
                    thickness = maxOf(current.thickness, style.thickness),
                    opacity = maxOf(current.opacity, style.opacity),
                )
            }
        }
        return resolved ?: EspOutlineStyle.DEFAULT
    }
}

/**
 * Tracks Glow contributors that can arrive at different points of one rendered frame.
 *
 * The first mask user clears stale pixels. Later users append to that same mask and only
 * contribute their style after geometry was actually submitted.
 */
internal class EspGlowFrameState {

    private var maskPrepared = false

    var hasContribution = false
        private set

    var style = EspGlowStyle.DEFAULT
        private set

    val hasMask: Boolean
        get() = maskPrepared

    fun prepareMask(): Boolean {
        val shouldClear = !maskPrepared
        maskPrepared = true
        return shouldClear
    }

    fun contribute(contribution: EspGlowStyle) {
        style = if (hasContribution) {
            EspShaderStyleResolver.resolveGlow(style, contribution)
        } else {
            contribution
        }
        hasContribution = true
    }

    fun reset() {
        maskPrepared = false
        hasContribution = false
        style = EspGlowStyle.DEFAULT
    }
}

/** Keeps every module source's mask lifecycle and style independently configurable. */
internal class EspGlowFrameSources {

    private val states = EnumMap<EspGlowSource, EspGlowFrameState>(EspGlowSource::class.java)

    val hasAnyContribution: Boolean
        get() = states.values.any(EspGlowFrameState::hasContribution)

    val activeSources: List<EspGlowSource>
        get() = EspGlowSource.entries.filter { state(it).hasContribution }

    val maskSources: List<EspGlowSource>
        get() = EspGlowSource.entries.filter { state(it).hasMask }

    fun state(source: EspGlowSource): EspGlowFrameState = states.getOrPut(source, ::EspGlowFrameState)

    fun prepareMask(source: EspGlowSource): Boolean = state(source).prepareMask()

    fun contribute(source: EspGlowSource, style: EspGlowStyle) = state(source).contribute(style)

    fun reset() {
        states.values.forEach(EspGlowFrameState::reset)
    }
}
