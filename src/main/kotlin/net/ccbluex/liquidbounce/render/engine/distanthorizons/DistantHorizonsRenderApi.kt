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

package net.ccbluex.liquidbounce.render.engine.distanthorizons

import org.joml.Matrix4f

/** Current DH render transform captured from its typed public render events. */
internal object DistantHorizonsRenderApi {

    @Volatile
    private var latestState: DistantHorizonsRenderState? = null

    fun captureRenderParam(
        renderParam: DistantHorizonsPublicRenderParam,
        frameToken: Long,
    ): DistantHorizonsRenderState {
        return DistantHorizonsRenderState(
            inverseMvmProjection = matrixFromDhValues(renderParam.inverseMvmProjection),
            nearClipPlane = renderParam.nearClipPlane,
            farClipPlane = renderParam.farClipPlane,
            frameToken = frameToken,
        ).also { latestState = it }
    }

    fun state(frameToken: Long? = null): DistantHorizonsRenderState? {
        val state = latestState ?: return null
        if (frameToken != null && state.frameToken != frameToken) return null
        return state.copy(inverseMvmProjection = Matrix4f(state.inverseMvmProjection))
    }

    fun invalidate() {
        latestState = null
    }
}

internal data class DistantHorizonsRenderState(
    val inverseMvmProjection: Matrix4f,
    val nearClipPlane: Float,
    val farClipPlane: Float,
    val frameToken: Long,
)

internal data class DistantHorizonsFogDistanceMapping(
    val scale: Float,
    val offset: Float,
    val maxDistance: Float,
) {
    fun mapDistance(distance: Float): Float = (distance * scale + offset).coerceIn(0f, maxDistance)

    companion object {
        fun from(nearClip: Float, farClip: Float, fogMaxDistance: Float): DistantHorizonsFogDistanceMapping {
            val safeMaximum = fogMaxDistance.coerceAtLeast(0f)
            val clipLength = farClip - nearClip
            if (!clipLength.isFinite() || clipLength <= 0f || safeMaximum <= 0f) {
                return DistantHorizonsFogDistanceMapping(1f, 0f, safeMaximum)
            }

            val scale = safeMaximum / clipLength
            return DistantHorizonsFogDistanceMapping(scale, -nearClip * scale, safeMaximum)
        }
    }
}

internal fun matrixFromDhValues(values: FloatArray): Matrix4f {
    require(values.size == MATRIX_VALUE_COUNT) { "DH matrix must contain $MATRIX_VALUE_COUNT values" }
    return Matrix4f().set(values)
}

internal fun distantHorizonsFogSaturationDistance(environmentalEnd: Float, renderDistanceEnd: Float): Float =
    minOf(environmentalEnd, renderDistanceEnd).coerceIn(MIN_FOG_SATURATION_DISTANCE, MAX_FOG_SATURATION_DISTANCE)

private const val MATRIX_VALUE_COUNT = 16
private const val MIN_FOG_SATURATION_DISTANCE = 16f
private const val MAX_FOG_SATURATION_DISTANCE = 1024f
