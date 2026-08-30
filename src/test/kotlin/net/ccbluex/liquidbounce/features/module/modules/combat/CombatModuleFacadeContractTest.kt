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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.MaceKillAttackHook
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillSetbackHook
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Locks the public module and Java-mixin surface before runtime responsibilities are extracted. */
class CombatModuleFacadeContractTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val SPEAR_FACADE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleSpearKill.kt"
        private const val SPEAR_BRIDGE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill/facade/SpearKillFacadeBridge.kt"
        private const val MACE_FACADE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleMaceKill.kt"
        private const val MACE_OPERATIONS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/macekill/orchestration/MaceKillFacadeOperations.kt"
        private const val AUTO_ROD =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleAutoRod.kt"
        private const val PROJECTILE_AIM =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/autoshoot/AutoShootGravityType.kt"
        private const val AUTO_ROD_GRAVITY =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/autoshoot/GravityType.kt"
        private val FORBIDDEN_FACADE_IMPORTS = listOf(
            ".spearkill.integration.",
            ".spearkill.runtime.",
            ".spearkill.session.",
            ".render.engine.esp.",
            ".utils.entity.",
            ".utils.item.",
        )
    }

    @Test
    fun `module identities remain stable`() {
        assertEquals("MaceKill", ModuleMaceKill.name)
        assertEquals(ModuleCategories.COMBAT, ModuleMaceKill.category)
        assertEquals("SpearKill", ModuleSpearKill.name)
        assertEquals(ModuleCategories.COMBAT, ModuleSpearKill.category)
        assertEquals(listOf("AutoSpear"), ModuleSpearKill.aliases)
        assertEquals("FightBot", ModuleFightBot.name)
        assertEquals(ModuleCategories.COMBAT, ModuleFightBot.category)
    }

    @Test
    fun `SpearKill keeps every Java mixin bridge`() {
        val facade = ModuleSpearKill::class.java

        assertStaticMethod(facade, "ownsKillAuraSpearUse")
        assertStaticMethod(facade, "routeRotationOverride")
        assertStaticMethod(facade, "shouldAnimateRaisedSpear")
        assertStaticMethod(facade, "raisedSpearHand")
        assertStaticMethod(facade, "getControlsSpearAnimation")
        assertStaticMethod(
            facade,
            "getSpearAnimationTicks",
            InteractionHand::class.java,
            Float::class.javaPrimitiveType!!,
        )
        assertStaticMethod(
            facade,
            "getSpearAnimationTicks",
            LivingEntity::class.java,
            Float::class.javaPrimitiveType!!,
        )
    }

    @Test
    fun `SpearKill facade delegates internal integration in its established order`() {
        val facade = Files.readString(Path.of(SPEAR_FACADE))
        val bridge = Files.readString(Path.of(SPEAR_BRIDGE))
        val facadeImports = facade.lineSequence().filter { it.startsWith("import ") }.toList()

        assertFalse(facadeImports.any { line -> FORBIDDEN_FACADE_IMPORTS.any(line::contains) })
        assertInOrder(
            facade,
            "SpearKillFacadeBridge.initializePreview(this)",
            "SpearKillFacadeBridge.registerHandlers(this)",
            "SpearKillFacadeBridge.disable(this)",
            "super.onDisabled()",
        )
        assertInOrder(
            bridge,
            "SpearKillSetbackHook.install(SpearKillSetbackCallbacks(",
            "beforeCorrection = module::prepareFacadeSetbackCorrection",
            "afterCorrection = module::finishFacadeSetbackCorrection",
            "registerRouteRotationHandler()",
            "registerMovementInputHandler()",
            "registerTickHandler()",
            "registerNetworkMovementHandler()",
            "registerServerSneakPacketHandler()",
            "registerPacketSafetyHandler()",
            "registerFallDamagePacketHandler()",
            "registerPacketDeliveryHandler()",
            "registerWorldChangeHandler()",
            "registerDisconnectHandler()",
            "registerRenderHandler()",
        )
    }

    @Test
    fun `MaceKill preview glow is registered by its existing feature package`() {
        val facade = Files.readString(Path.of(MACE_FACADE))
        val operations = Files.readString(Path.of(MACE_OPERATIONS))

        assertFalse(facade.contains("import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry"))
        assertInOrder(
            facade,
            "registerMaceKillPreviewGlow()",
            "MaceKillAttackHook.install(::handleAcceptedAttack)",
            "installMaceKillControlRegistries()",
        )
        assertTrue(operations.contains("TargetGlowSourceRegistry.register(::currentPreviewGlow)"))
        assertInOrder(
            operations,
            "RemoteKillSetbackRegistry.register(setbackListener)",
            "MaceClipResearchControlRegistry.install(researchControl)",
        )
        assertFalse(facade.contains(".macekill.maceclip."))
        assertFalse(facade.contains(".combat.remotekill."))
    }

    @Test
    fun `AutoRod projectile aim delegates without leaking shared projectile packages into root`() {
        val facade = Files.readString(Path.of(AUTO_ROD))
        val projectileAim = Files.readString(Path.of(PROJECTILE_AIM))
        val gravity = Files.readString(Path.of(AUTO_ROD_GRAVITY))

        assertFalse(facade.contains("import net.ccbluex.liquidbounce.utils.aiming.projectiles."))
        assertFalse(facade.contains("import net.ccbluex.liquidbounce.utils.render.trajectory."))
        assertTrue(facade.contains("gravityType.apply(target, player.eyePosition, pointTracker)"))
        assertTrue(gravity.contains("PROJECTILE -> calculateFishingRodRotation(target)"))
        assertTrue(projectileAim.contains(
            "SituationalProjectileAngleCalculator.calculateAngleForEntity(TrajectoryInfo.FISHING_ROD, target)"
        ))
    }

    @Test
    fun `Java hooks keep their root package entry points`() {
        assertNotNull(MaceKillAttackHook::class.java.getDeclaredMethod("commit", Class.forName(
            "net.minecraft.world.entity.player.Player",
        ), Class.forName("net.minecraft.world.entity.Entity")))
        assertNotNull(SpearKillSetbackHook::class.java.getDeclaredMethod(
            "beforeCorrection",
            Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket"),
            Class.forName("net.minecraft.world.entity.player.Player"),
        ))
        assertNotNull(SpearKillSetbackHook::class.java.getDeclaredMethod(
            "afterCorrection",
            Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket"),
            Class.forName("net.minecraft.world.entity.player.Player"),
        ))
    }

    private fun assertStaticMethod(type: Class<*>, name: String, vararg parameters: Class<*>) {
        val method = type.getDeclaredMethod(name, *parameters)
        assertEquals(true, java.lang.reflect.Modifier.isStatic(method.modifiers), name)
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

}
