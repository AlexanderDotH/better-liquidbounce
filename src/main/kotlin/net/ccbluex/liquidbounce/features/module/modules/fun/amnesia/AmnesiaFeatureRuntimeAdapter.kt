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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaActionContributions
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeHook
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaVisualEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal object AmnesiaFeatureRuntimeAdapter : AmnesiaRuntimeHook {
    override fun findTarget() = ModuleAmnesia.findTarget()

    override fun auxiliaryVisualPosition(entity: LivingEntity, partialTicks: Float) =
        ModuleAmnesia.getAuxiliaryVisualPosition(entity, partialTicks)

    override fun actionContributions(entity: LivingEntity) = AmnesiaActionContributions(
        criticals = FakeCriticals.getActionState(entity),
        jesus = FakeJesus.getActionState(entity),
        scaffold = FakeScaffold.getActionState(entity),
        bhop = FakeBhop.getActionState(entity),
        fakeSneak = FakeSneak.running,
    )

    override fun visualEffects(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ): AmnesiaVisualEffects {
        val jesus = FakeJesus.takeIf { it.running }?.getTransform(entity, partialTicks, basePosition)
        val adjustedBasePosition = jesus?.position ?: basePosition
        val bhop = FakeBhop.takeIf { it.running }
            ?.getTransform(entity, partialTicks, adjustedBasePosition, velocityPositionActive)
        val criticals = FakeCriticals.takeIf { it.running }
            ?.getTransform(entity, partialTicks, adjustedBasePosition, velocityPositionActive)
        return AmnesiaVisualEffects(
            spinbot = FakeSpinbot.takeIf { it.running }?.getTransform(entity),
            jesus = jesus,
            bhop = bhop,
            criticals = criticals,
            scaffold = FakeScaffold.takeIf { it.running }?.getTransform(entity),
            criticalsHasRotation = criticals != null && FakeCriticals.hasRotation(entity),
            bhopHasRotation = bhop != null && FakeBhop.hasRotation(entity),
        )
    }

    override fun delayPlayerModelRunning() = DelayPlayerModel.running
    override fun fakeKillAuraRunning() = FakeKillAura.running
    override fun fakeVelocityRunning() = FakeVelocity.running
    override fun fakeVelocityMode() = FakeVelocity.mode
    override fun clearScaffoldRenderState() = FakeScaffold.clearRenderState()
}
