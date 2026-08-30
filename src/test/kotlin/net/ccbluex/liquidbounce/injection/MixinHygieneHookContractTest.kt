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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MixinHygieneHookContractTest {

    @Test
    fun `minecraft title hook preserves injection metadata laziness and composition order`() {
        val mixin = read(MIXIN_MINECRAFT)
        val injection = mixin.method("private void getClientTitle")
        val hook = read(TITLE_HOOK)

        assertTrue(
            mixin.compact().contains(
                "@Inject(method=\"createTitle\",at=@At(" +
                    "value=\"INVOKE\",target=\"$TITLE_APPEND_DESCRIPTOR\",ordinal=1),cancellable=true)",
            ),
        )
        assertTrue(injection.contains("private void getClientTitle(CallbackInfoReturnable<String> callback)"))
        injection.assertInOrder("MinecraftTitleHook.buildTitle(", "if (title != null)", "callback.setReturnValue(title)")

        hook.assertInOrder(
            "MinecraftClientFeatureBridge.isAppearanceHidden()",
            "ClientUtilsKt.getLogger().debug",
            "createBaseTitle()",
            "appendBrowserAcceleration",
            "appendConnectionState",
            "return titleBuilder.toString()",
        )
        hook.assertInOrder(
            "MinecraftClientFeatureBridge.isAppearanceHidden()",
            "hotkeyAvailable.getAsBoolean()",
            "connectionSupplier.get()",
            "serverDataSupplier.get()",
            "integratedServerSupplier.get()",
        )
    }

    @Test
    fun `player attack sound hooks preserve targets ordinals signatures and sound order`() {
        val mixin = read(MIXIN_PLAYER)
        val hook = read(PLAYER_SOUND_HOOK)
        val compact = mixin.compact()

        assertInjection(compact, "attack", 0)
        assertInjection(compact, "attack", 1)
        assertInjection(compact, "attackVisualEffects", 0)
        assertInjection(compact, "attackVisualEffects", 1)
        assertInjection(compact, "doSweepAttack", 0)
        listOf(
            "private void hookPlaySound(Entity target, CallbackInfo ci)",
            "private void hookPlaySound1(Entity target, CallbackInfo ci)",
            "private void hookPlaySound2(Entity target, boolean criticalHit, boolean sweeping, " +
                "boolean cooldownPassed, boolean pierce, float enchantDamage, CallbackInfo ci)",
            "private void hookPlaySound3(Entity target, boolean criticalHit, boolean sweeping, " +
                "boolean cooldownPassed, boolean pierce, float enchantDamage, CallbackInfo ci)",
            "private void hookPlaySound4(Entity target, float damage, DamageSource damageSource, " +
                "float cooldownProgress, CallbackInfo ci)",
        ).forEach { signature -> assertTrue(mixin.contains(signature), signature) }

        listOf(
            "hookPlaySound" to "SoundEvents.PLAYER_ATTACK_KNOCKBACK",
            "hookPlaySound1" to "SoundEvents.PLAYER_ATTACK_NODAMAGE",
            "hookPlaySound2" to "SoundEvents.PLAYER_ATTACK_CRIT",
            "hookPlaySound3" to
                "cooldownPassed ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK",
            "hookPlaySound4" to "SoundEvents.PLAYER_ATTACK_SWEEP",
        ).forEach { (method, sound) ->
            mixin.method("private void $method").assertInOrder(
                "!ModuleHitFX.INSTANCE.getRunning()",
                "PlayerAttackSoundHook.playIfFakePlayer",
                sound,
            )
        }

        hook.assertInOrder(FAKE_PLAYER_CLASS, "target.getClass()", "type.getSuperclass()", "level().playSound")
    }

    @Test
    fun `game renderer bobbing hook preserves cancellability gates and transform order`() {
        val mixin = read(MIXIN_GAME_RENDERER)
        val injection = mixin.method("private void injectBobView")
        val hook = read(BOBBING_HOOK)

        assertTrue(
            mixin.compact().contains(
                "@Inject(method=\"bobView\",at=@At(\"HEAD\"),cancellable=true)" +
                    "privatevoidinjectBobView(CameraRenderStatecameraState,PoseStackposeStack,CallbackInfoci)",
            ),
        )
        injection.assertInOrder("GameRendererBobbingHook.apply(cameraState, poseStack)", "ci.cancel()")
        hook.assertInOrder(
            "if (shouldSuppressVanillaBobbing())",
            "return true",
            "if (!ModuleDankBobbing.INSTANCE.getRunning())",
        )
        hook.assertInOrder(
            "if (!ModuleDankBobbing.INSTANCE.getRunning())",
            "return false",
            "if (!entityRenderState.isPlayer)",
            "return false",
            "poseStack.translate",
        )
        hook.assertInOrder(
            "shouldSuppressVanillaBobbing()",
            "ModuleDankBobbing.INSTANCE.getRunning()",
            "entityRenderState.isPlayer",
            "poseStack.translate",
            "Axis.ZP.rotationDegrees",
            "Axis.XP.rotationDegrees",
            "return true",
        )
    }

    private fun assertInjection(source: String, method: String, ordinal: Int) {
        val annotation = "@Inject(method=\"$method\",at=@At(value=\"INVOKE\",target=" +
            "\"$PLAY_SOUND_DESCRIPTOR\",ordinal=$ordinal))"
        assertTrue(source.contains(annotation), "$method ordinal $ordinal")
        val annotationEnd = source.indexOf(annotation) + annotation.length
        val methodEnd = source.indexOf('{', annotationEnd)
        assertFalse(source.substring(annotationEnd, methodEnd).contains("cancellable=true"), method)
    }

    private fun read(path: String) = Files.readString(Path.of(path))

    private fun String.compact() = replace(Regex("\\s+"), "")

    private fun String.method(signature: String): String {
        val start = indexOf(signature)
        assertTrue(start >= 0, signature)
        val nextMethod = indexOf("\n    @", start + signature.length)
        return substring(start, if (nextMethod < 0) length else nextMethod)
    }

    private fun String.assertInOrder(vararg operations: String) {
        var offset = 0
        val positions = operations.map { operation ->
            val position = indexOf(operation, offset)
            if (position >= 0) {
                offset = position + operation.length
            }
            position
        }
        assertTrue(positions.all { it >= 0 }, operations.joinToString())
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right }, operations.joinToString())
    }

    private companion object {
        const val MIXIN_MINECRAFT =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinMinecraft.java"
        const val MIXIN_PLAYER =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/entity/MixinPlayer.java"
        const val MIXIN_GAME_RENDERER =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinGameRenderer.java"
        const val TITLE_HOOK = "src/main/java/net/ccbluex/liquidbounce/injection/hooks/MinecraftTitleHook.java"
        const val PLAYER_SOUND_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/PlayerAttackSoundHook.java"
        const val BOBBING_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/GameRendererBobbingHook.java"
        const val TITLE_APPEND_DESCRIPTOR =
            "Ljava/lang/StringBuilder;append(Ljava/lang/String;)Ljava/lang/StringBuilder;"
        const val PLAY_SOUND_DESCRIPTOR =
            "Lnet/minecraft/world/entity/player/Player;" +
                "playServerSideSound(Lnet/minecraft/sounds/SoundEvent;)V"
        const val FAKE_PLAYER_CLASS =
            "net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer.FakePlayer"
    }
}
