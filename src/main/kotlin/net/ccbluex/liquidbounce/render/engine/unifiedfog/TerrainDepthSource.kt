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

package net.ccbluex.liquidbounce.render.engine.unifiedfog

import org.joml.Matrix4f
import org.joml.Matrix4fc
import kotlin.math.abs
import kotlin.math.max

internal data class TerrainFrameToken(
    val lifecycleGeneration: Long,
    val frameIndex: Long,
) {
    init {
        require(lifecycleGeneration >= 0) { "Lifecycle generation must not be negative" }
        require(frameIndex >= 0) { "Frame index must not be negative" }
    }
}

internal data class FrameDimensions(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive" }
    }

    fun hasCompatibleAspect(other: FrameDimensions, tolerance: Float): Boolean {
        val sourceAspect = width.toFloat() / height
        val otherAspect = other.width.toFloat() / other.height
        val relativeDifference = abs(sourceAspect - otherAspect) / max(sourceAspect, otherAspect)
        return relativeDifference <= tolerance
    }
}

internal enum class TerrainDepthKind {
    VANILLA,
    DISTANT_HORIZONS,
}

internal enum class ClipDepthRange {
    NEGATIVE_ONE_TO_ONE,
    ZERO_TO_ONE,
}

internal data class TerrainDepthConvention(
    val clearDepth: Float,
    val clipDepthRange: ClipDepthRange,
) {
    init {
        require(clearDepth.isFinite() && clearDepth in 0f..1f) {
            "Clear depth must be a finite normalized value"
        }
    }
}

internal data class TerrainClipRange(
    val nearPlane: Float,
    val farPlane: Float,
) {
    init {
        require(nearPlane.isFinite() && nearPlane >= 0f) { "Near clip plane must be finite and non-negative" }
        require(farPlane.isFinite() && farPlane > nearPlane) { "Far clip plane must be finite and beyond near" }
    }
}

internal class InverseReconstructionMatrix(matrix: Matrix4fc) {

    private val elements = FloatArray(MATRIX_ELEMENT_COUNT).also(matrix::get)

    init {
        require(elements.all(Float::isFinite)) { "Inverse reconstruction matrix must be finite" }
    }

    fun copy(): Matrix4f = Matrix4f().set(elements)

    override fun equals(other: Any?): Boolean =
        other is InverseReconstructionMatrix && elements.contentEquals(other.elements)

    override fun hashCode(): Int = elements.contentHashCode()

    companion object {
        private const val MATRIX_ELEMENT_COUNT = 16
    }
}

internal data class TerrainDepthSource<T : Any>(
    val kind: TerrainDepthKind,
    val textureView: T,
    val depthConvention: TerrainDepthConvention,
    val inverseReconstruction: InverseReconstructionMatrix,
    val clipRange: TerrainClipRange,
    val dimensions: FrameDimensions,
    val frameToken: TerrainFrameToken,
)

internal enum class TerrainDepthUnavailableReason {
    DEPTH_NOT_READY,
    UNSUPPORTED_CAPABILITY,
    BACKEND_CHANGED,
}

internal sealed interface OptionalTerrainDepthSource<out T : Any> {
    data object Absent : OptionalTerrainDepthSource<Nothing>

    data class Ready<T : Any>(val source: TerrainDepthSource<T>) : OptionalTerrainDepthSource<T>

    data class Unavailable(
        val reason: TerrainDepthUnavailableReason,
    ) : OptionalTerrainDepthSource<Nothing>
}

internal enum class TerrainDepthSourceRejectionReason {
    UNAVAILABLE,
    WRONG_SOURCE_KIND,
    STALE_FRAME,
    WRONG_SIZE,
}

internal data class TerrainDepthSourceRejection(
    val sourceKind: TerrainDepthKind,
    val reason: TerrainDepthSourceRejectionReason,
    val unavailableReason: TerrainDepthUnavailableReason? = null,
)

internal data class TerrainDepthValidationPolicy private constructor(
    val maximumFrameAge: Long,
    val allowSameAspectScaling: Boolean,
    val aspectRatioTolerance: Float,
) {
    init {
        require(maximumFrameAge >= 0L) { "Maximum frame age must not be negative" }
        require(aspectRatioTolerance.isFinite() && aspectRatioTolerance >= 0f) {
            "Aspect ratio tolerance must be finite and non-negative"
        }
    }

    companion object {
        const val RENDER_ASPECT_TOLERANCE = 0.02f
        val STRICT = TerrainDepthValidationPolicy(0L, false, 0f)

        fun renderCompatible(maximumFrameAge: Long) = TerrainDepthValidationPolicy(
            maximumFrameAge = maximumFrameAge,
            allowSameAspectScaling = true,
            aspectRatioTolerance = RENDER_ASPECT_TOLERANCE,
        )
    }
}

internal object TerrainDepthSourceValidator {

    fun validate(
        source: TerrainDepthSource<*>,
        expectedKind: TerrainDepthKind,
        expectedFrameToken: TerrainFrameToken,
        expectedDimensions: FrameDimensions,
        policy: TerrainDepthValidationPolicy = TerrainDepthValidationPolicy.STRICT,
    ): TerrainDepthSourceRejection? {
        if (source.kind != expectedKind) {
            return TerrainDepthSourceRejection(source.kind, TerrainDepthSourceRejectionReason.WRONG_SOURCE_KIND)
        }
        if (!frameIsAccepted(source.frameToken, expectedFrameToken, policy.maximumFrameAge)) {
            return TerrainDepthSourceRejection(source.kind, TerrainDepthSourceRejectionReason.STALE_FRAME)
        }
        if (!dimensionsAreAccepted(source.dimensions, expectedDimensions, policy)) {
            return TerrainDepthSourceRejection(source.kind, TerrainDepthSourceRejectionReason.WRONG_SIZE)
        }
        return null
    }

    private fun frameIsAccepted(source: TerrainFrameToken, expected: TerrainFrameToken, maximumAge: Long): Boolean {
        if (source.lifecycleGeneration != expected.lifecycleGeneration) return false
        val age = expected.frameIndex - source.frameIndex
        return age in 0L..maximumAge
    }

    private fun dimensionsAreAccepted(
        source: FrameDimensions,
        expected: FrameDimensions,
        policy: TerrainDepthValidationPolicy,
    ): Boolean {
        if (source == expected) return true
        if (!policy.allowSameAspectScaling) return false
        return source.hasCompatibleAspect(expected, policy.aspectRatioTolerance)
    }
}
