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

package net.ccbluex.liquidbounce.injection

import net.ccbluex.liquidbounce.interfaces.ClientLevelFeatureBridge
import net.ccbluex.liquidbounce.interfaces.ClientLevelFeatureProvider
import net.ccbluex.liquidbounce.interfaces.MinecraftClientFeatureBridge
import net.ccbluex.liquidbounce.interfaces.MinecraftClientFeatureProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class MinecraftClientFeatureBridgeContractTest {
    @Test
    fun `minecraft client feature bridge keeps static mixin signatures and safe defaults`() {
        assertStaticBoolean(MinecraftClientFeatureBridge::class.java, "isAppearanceHidden")
        assertStaticVoid(MinecraftClientFeatureBridge::class.java, "onGameTick")
        assertStaticBoolean(MinecraftClientFeatureBridge::class.java, "claimReachUse")
        assertStaticBoolean(MinecraftClientFeatureBridge::class.java, "hasEnforcedBlockingHand")
        assertStaticBoolean(MinecraftClientFeatureBridge::class.java, "shouldPauseCombat")
        assertStaticVoid(MinecraftClientFeatureBridge::class.java, "resetPlayerModelState")

        assertFalse(MinecraftClientFeatureBridge.isAppearanceHidden())
        assertFalse(MinecraftClientFeatureBridge.claimReachUse())
        assertFalse(MinecraftClientFeatureBridge.hasEnforcedBlockingHand())
        assertFalse(MinecraftClientFeatureBridge.shouldPauseCombat())
        MinecraftClientFeatureBridge.onGameTick()
        MinecraftClientFeatureBridge.resetPlayerModelState()
    }

    @Test
    fun `client level bridge keeps static boolean signatures and disabled module defaults`() {
        assertStaticBoolean(ClientLevelFeatureBridge::class.java, "canRenderExplosionParticles")
        assertStaticBoolean(ClientLevelFeatureBridge::class.java, "canRenderBlockBreakParticles")
        assertStaticBoolean(ClientLevelFeatureBridge::class.java, "canPushEntities")
        assertStaticBoolean(ClientLevelFeatureBridge::class.java, "canPushFishingRod")

        assertTrue(ClientLevelFeatureBridge.canRenderExplosionParticles())
        assertTrue(ClientLevelFeatureBridge.canRenderBlockBreakParticles())
        assertTrue(ClientLevelFeatureBridge.canPushEntities())
        assertTrue(ClientLevelFeatureBridge.canPushFishingRod())
    }

    @Test
    fun `installed minecraft provider supplies every value and rejects duplicate installation`() {
        val provider = RecordingMinecraftProvider()

        MinecraftClientFeatureBridge.withProviderForTest(provider) {
            assertTrue(MinecraftClientFeatureBridge.isAppearanceHidden())
            assertTrue(MinecraftClientFeatureBridge.claimReachUse())
            assertTrue(MinecraftClientFeatureBridge.hasEnforcedBlockingHand())
            assertTrue(MinecraftClientFeatureBridge.shouldPauseCombat())
            MinecraftClientFeatureBridge.onGameTick()
            MinecraftClientFeatureBridge.resetPlayerModelState()
            assertTrue(provider.tickCalled)
            assertTrue(provider.resetCalled)
            assertThrows(IllegalStateException::class.java) {
                MinecraftClientFeatureBridge.install(provider)
            }
        }
    }

    @Test
    fun `installed client level provider overrides safe defaults and rejects duplicate installation`() {
        val provider = DisabledClientLevelProvider()

        ClientLevelFeatureBridge.withProviderForTest(provider) {
            assertFalse(ClientLevelFeatureBridge.canRenderExplosionParticles())
            assertFalse(ClientLevelFeatureBridge.canRenderBlockBreakParticles())
            assertFalse(ClientLevelFeatureBridge.canPushEntities())
            assertFalse(ClientLevelFeatureBridge.canPushFishingRod())
            assertThrows(IllegalStateException::class.java) {
                ClientLevelFeatureBridge.install(provider)
            }
        }
    }

    @Test
    fun `feature adapters preserve the exact lazy delegates`() {
        val minecraft = read(MINECRAFT_ADAPTER)
        val clientLevel = read(CLIENT_LEVEL_ADAPTER)

        listOf(
            "HideAppearance.isHidingNow",
            "ServerPlayerModelStateTracker.onGameTick()",
            "ReachInteractableFeature.claimUse()",
            "KillAuraAutoBlock.running && KillAuraAutoBlock.enforcedBlockingHand != null",
            "CombatManager.shouldPauseCombat",
            "ServerPlayerModelStateTracker.reset()",
        ).forEach { delegate -> assertTrue(minecraft.contains(delegate), delegate) }
        listOf(
            "ModuleAntiBlind.canRender(DoRender.EXPLOSION_PARTICLES)",
            "ModuleAntiBlind.canRender(DoRender.BLOCK_BREAK_PARTICLES)",
            "ModuleNoPush.canPush(NoPushBy.ENTITIES)",
            "ModuleNoPush.canPush(NoPushBy.FISHING_ROD)",
        ).forEach { delegate -> assertTrue(clientLevel.contains(delegate), delegate) }
    }

    @Test
    fun `java mixins call only the stable feature bridges at the original hook sites`() {
        val minecraft = read(MIXIN_MINECRAFT)
        val minecraftTitleHook = read(MINECRAFT_TITLE_HOOK)
        val clientLevel = read(MIXIN_CLIENT_LEVEL)
        val fishingHook = read(MIXIN_FISHING_HOOK)

        listOf(
            "MinecraftClientFeatureBridge.onGameTick()",
            "MinecraftClientFeatureBridge.claimReachUse()",
            "MinecraftClientFeatureBridge.hasEnforcedBlockingHand()",
            "MinecraftClientFeatureBridge.shouldPauseCombat()",
            "MinecraftClientFeatureBridge.resetPlayerModelState()",
        ).forEach { call -> assertTrue(minecraft.contains(call), call) }
        assertTrue(minecraft.contains("MinecraftTitleHook.buildTitle("))
        assertTrue(minecraftTitleHook.contains("MinecraftClientFeatureBridge.isAppearanceHidden()"))
        listOf(
            "ClientLevelFeatureBridge.canRenderExplosionParticles()",
            "ClientLevelFeatureBridge.canRenderBlockBreakParticles()",
            "ClientLevelFeatureBridge.canPushEntities()",
        ).forEach { call -> assertTrue(clientLevel.contains(call), call) }
        assertTrue(fishingHook.contains("ClientLevelFeatureBridge.canPushFishingRod()"))

        FORBIDDEN_MINECRAFT_IMPORTS.forEach { import ->
            assertFalse(minecraft.contains(import), import)
            assertFalse(minecraftTitleHook.contains(import), import)
        }
        FORBIDDEN_CLIENT_LEVEL_IMPORTS.forEach { import -> assertFalse(clientLevel.contains(import), import) }
        assertFalse(fishingHook.contains("import net.ccbluex.liquidbounce.features."))
    }

    private fun assertStaticBoolean(type: Class<*>, name: String) {
        val method = type.getDeclaredMethod(name)
        assertTrue(Modifier.isStatic(method.modifiers), name)
        assertTrue(method.returnType == Boolean::class.javaPrimitiveType, name)
    }

    private fun assertStaticVoid(type: Class<*>, name: String) {
        val method = type.getDeclaredMethod(name)
        assertTrue(Modifier.isStatic(method.modifiers), name)
        assertTrue(method.returnType == Void.TYPE, name)
    }

    private fun read(path: String) = Files.readString(Path.of(path))

    private class RecordingMinecraftProvider : MinecraftClientFeatureProvider {
        var tickCalled = false
        var resetCalled = false

        override fun isAppearanceHidden() = true
        override fun onGameTick() {
            tickCalled = true
        }

        override fun claimReachUse() = true
        override fun hasEnforcedBlockingHand() = true
        override fun shouldPauseCombat() = true
        override fun resetPlayerModelState() {
            resetCalled = true
        }
    }

    private class DisabledClientLevelProvider : ClientLevelFeatureProvider {
        override fun canRenderExplosionParticles() = false
        override fun canRenderBlockBreakParticles() = false
        override fun canPushEntities() = false
        override fun canPushFishingRod() = false
    }

    private companion object {
        const val MIXIN_MINECRAFT =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinMinecraft.java"
        const val MINECRAFT_TITLE_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/MinecraftTitleHook.java"
        const val MIXIN_CLIENT_LEVEL =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinClientLevel.java"
        const val MIXIN_FISHING_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/entity/projectile/MixinFishingHook.java"
        const val MINECRAFT_ADAPTER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/injection/MinecraftClientFeatureAdapter.kt"
        const val CLIENT_LEVEL_ADAPTER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/injection/ClientLevelFeatureAdapter.kt"

        val FORBIDDEN_MINECRAFT_IMPORTS = listOf(
            "import net.ccbluex.liquidbounce.features.misc.HideAppearance;",
            "import net.ccbluex.liquidbounce.features.combat.runtime.CombatManager;",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock;",
            "import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.ReachInteractableFeature;",
            "import net.ccbluex.liquidbounce.features.module.modules.render.playermodel.ServerPlayerModelStateTracker;",
        )
        val FORBIDDEN_CLIENT_LEVEL_IMPORTS = listOf(
            "import net.ccbluex.liquidbounce.features.module.modules.movement.NoPushBy;",
            "import net.ccbluex.liquidbounce.features.module.modules.render.DoRender;",
        )
    }
}
