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
@file:JvmName("WireframePlayerKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.wireframe

import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.world.phys.AABB

// pixels / (16 + 16)
internal val LIMB = AABB(0.0, 0.0, 0.0, 0.125, 0.375, 0.125)
internal val BODY = AABB(0.0, 0.0, 0.0, 0.25, 0.375, 0.125)
internal val HEAD = AABB(0.0, 0.0, 0.0, 0.25, 0.25, 0.25)

internal val RENDER_LEFT_LEG: AABB = LIMB.move(-LIMB.maxX, 0.0, 0.0)
internal val RENDER_RIGHT_LEG: AABB = LIMB
internal val RENDER_BODY: AABB = BODY.move(-LIMB.maxX, LIMB.maxY, 0.0)
internal val RENDER_LEFT_ARM: AABB = LIMB.move(-2 * LIMB.maxX, LIMB.maxY, 0.0)
internal val RENDER_RIGHT_ARM: AABB = LIMB.move(BODY.maxX - LIMB.maxX, LIMB.maxY, 0.0)
internal val RENDER_HEAD: AABB = HEAD.move(-LIMB.maxX, LIMB.maxY * 2, -HEAD.maxZ * 0.25)

internal const val MODEL_SCALE = 1.9f

internal const val CROUCH_BODY_ROTATION = 28.64789f
internal const val CROUCH_ARM_ROTATION = 22.918312f

internal val CROUCH_LEFT_LEG: AABB = RENDER_LEFT_LEG.move(0.0, 0.0, 0.125)
internal val CROUCH_RIGHT_LEG: AABB = RENDER_RIGHT_LEG.move(0.0, 0.0, 0.125)
internal val CROUCH_BODY: AABB = RENDER_BODY.move(0.0, -0.12, 0.05)
internal val CROUCH_LEFT_ARM: AABB = RENDER_LEFT_ARM.move(0.0, -0.12, 0.03)
internal val CROUCH_RIGHT_ARM: AABB = RENDER_RIGHT_ARM.move(0.0, -0.12, 0.03)
internal val CROUCH_HEAD: AABB = RENDER_HEAD.move(0.0, -0.18, 0.1)

internal const val SWIM_PART_ROTATION = 90f
internal const val SWIM_HEAD_TARGET_ROTATION = -45f
internal const val SWIM_LEFT_ARM_ROLL = -15f
internal const val SWIM_RIGHT_ARM_ROLL = 15f
internal const val SWIM_LEFT_LEG_ROLL = -6f
internal const val SWIM_RIGHT_LEG_ROLL = 6f
internal const val SWIM_ROOT_Y_OFFSET = -0.4375
