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
package net.ccbluex.liquidbounce.bootstrap.module

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.render.*
import net.ccbluex.liquidbounce.features.module.modules.render.animations.ModuleAnimations
import net.ccbluex.liquidbounce.features.module.modules.render.cameraclip.ModuleCameraClip
import net.ccbluex.liquidbounce.features.module.modules.render.crosshair.ModuleCrosshair
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience
import net.ccbluex.liquidbounce.features.module.modules.render.esp.ModuleESP
import net.ccbluex.liquidbounce.features.module.modules.render.hats.ModuleHats
import net.ccbluex.liquidbounce.features.module.modules.render.hitfx.ModuleHitFX
import net.ccbluex.liquidbounce.features.module.modules.render.jumpeffect.ModuleJumpEffect
import net.ccbluex.liquidbounce.features.module.modules.render.murdermystery.ModuleMurderMystery
import net.ccbluex.liquidbounce.features.module.modules.render.nametags.ModuleNametags
import net.ccbluex.liquidbounce.features.module.modules.render.potionfx.ModulePotionFX
import net.ccbluex.liquidbounce.features.module.modules.render.totemeffect.ModuleTotemEffect
import net.ccbluex.liquidbounce.features.module.modules.render.trajectories.ModuleTrajectories
import net.ccbluex.liquidbounce.features.module.modules.render.wings.ModuleWings

internal val renderModules: Array<ClientModule> = arrayOf(
    ModuleAnimations,
    ModuleAntiBlind,
    ModuleBetterInventory,
    ModuleBlockESP,
    ModuleBlockOutline,
    ModuleBreadcrumbs,
    ModuleCameraClip,
    ModuleClickGui,
    ModuleDamageParticles,
    ModuleParticles,
    ModuleESP,
    ModuleLogoffSpot,
    ModuleFreeCam,
    ModuleSmoothCamera,
    ModuleFreeLook,
    ModuleFullBright,
    ModuleHoleESP,
    ModuleHud,
    ModuleHats,
    ModuleItemESP,
    ModuleOrbESP,
    ModuleItemTags,
    ModuleJumpEffect,
    ModuleMobOwners,
    ModuleMurderMystery,
    ModuleHitFX,
    ModuleNametags,
    ModuleCombineMobs,
    ModuleAspect,
    ModuleAutoF5,
    ModuleChams,
    ModuleBedPlates,
    ModuleNoBob,
    ModuleNoFov,
    ModuleNoHurtCam,
    ModuleNoSwing,
    ModuleCustomAmbience,
    ModuleProphuntESP,
    ModuleQuickPerspectiveSwap,
    ModuleRadar,
    ModulePlayerModel,
    ModuleSilentHotbar,
    ModuleStorageESP,
    ModuleTNTTimer,
    ModuleTracers,
    ModuleTrajectories,
    ModuleTrueSight,
    ModuleVoidESP,
    ModuleXRay,
    ModuleDebug,
    ModuleZoom,
    ModuleItemChams,
    ModuleCrystalView,
    ModuleSkinChanger,
    ModuleProtectionZones,
    ModuleCrosshair,
    ModuleWings,
    ModulePotionFX,
    ModuleTotemEffect,
)
