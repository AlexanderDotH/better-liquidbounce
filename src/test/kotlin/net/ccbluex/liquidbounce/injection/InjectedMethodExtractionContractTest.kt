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
package net.ccbluex.liquidbounce.injection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InjectedMethodExtractionContractTest {

    @Test
    fun `local player raycast keeps target descriptor signature and decision order`() {
        val mixin = read(MIXIN_LOCAL_PLAYER)
        val method = mixin.method("private static HitResult hookRaycast")
        val hook = read(LOCAL_PLAYER_RAYCAST_HOOK)

        assertTrue(
            mixin.compact().contains(
                "@ModifyExpressionValue(method=\"$PICK_DESCRIPTOR\",at=@At(" +
                    "value=\"INVOKE\",target=\"$ENTITY_PICK_DESCRIPTOR\"))",
            ),
        )
        assertTrue(
            method.contains(
                "private static HitResult hookRaycast(HitResult original, Entity camera, " +
                    "double blockInteractionRange, double entityInteractionRange, float tickDelta)",
            ),
        )
        method.assertInOrder(
            "LocalPlayerRaycastHook.modify(",
            "original",
            "camera",
            "blockInteractionRange",
            "entityInteractionRange",
            "tickDelta",
        )
        hook.assertInOrder(
            "camera != Minecraft.getInstance().player",
            "return original",
            "new Rotation(camera.getViewYRot(tickDelta), camera.getViewXRot(tickDelta), true)",
            "selectRotation(cameraRotation)",
            "findThroughWallsEntity(rotation)",
            "RaytracingKt.traceFromPlayer",
            "ModuleLiquidPlace.INSTANCE.getRunning()",
        )
    }

    @Test
    fun `first person item transform keeps injection point and transform branch order`() {
        val mixin = read(MIXIN_ITEM_IN_HAND_RENDERER)
        val method = mixin.method("private void hookRenderFirstPersonItem")
        val hook = read(FIRST_PERSON_ITEM_TRANSFORM_HOOK)
        val compact = mixin.compact()

        assertTrue(
            compact.contains(
                "@Inject(method=\"submitArmWithItem\",at=@At(value=\"INVOKE\"," +
                    "target=\"$PUSH_POSE_DESCRIPTOR\",shift=At.Shift.AFTER))",
            ),
        )
        assertFalse(method.substringBefore('{').contains("cancellable"))
        assertTrue(method.contains("CallbackInfo ci"))
        method.assertInOrder(
            "FirstPersonItemTransformHook.apply(",
            "hand",
            "itemStack",
            "offHandItem",
            "poseStack",
        )
        hook.assertInOrder(
            "ModuleAnimations.INSTANCE.getRunning()",
            "InteractionHand.MAIN_HAND == hand",
            "itemStack.has(DataComponents.MAP_ID)",
            "offHandItem.isEmpty()",
            "mainHand.getRunning() && offHand.getRunning()",
            "applyBothHandsTransform",
            "isInBothHands && mainHand.getRunning()",
            "poseStack.translate",
            "InteractionHand.MAIN_HAND == hand && mainHand.getRunning()",
            "ModuleAnimationsKt.shouldApplyOffHandTransform",
        )
        hook.method("private static void applyBothHandsTransform").assertInOrder(
            "mainHand.getMainHandX() + offHand.getOffHandX()",
            "mainHand.getMainHandY() + offHand.getOffHandY()",
            "mainHand.getMainHandItemScale() + offHand.getOffHandItemScale()",
            "mainHand.getMainHandPositiveX() + offHand.getOffHandPositiveX()",
            "mainHand.getMainHandPositiveY() + offHand.getOffHandPositiveY()",
            "mainHand.getMainHandPositiveZ() + offHand.getOffHandPositiveZ()",
        )
        hook.assertInOrder(
            "matrices.translate",
            "Axis.XP.rotationDegrees",
            "Axis.YP.rotationDegrees",
            "Axis.ZP.rotationDegrees",
        )
    }

    @Test
    fun `connection receiving hook cancels bundle before ordered recursive dispatch`() {
        val mixin = read(MIXIN_CONNECTION)
        val method = mixin.method("private static void hookReceivingPacket")
        val hook = read(CONNECTION_RECEIVING_PACKET_HOOK)

        assertTrue(
            mixin.compact().contains(
                "@Inject(method=\"genericsFtw\",at=@At(\"HEAD\"),cancellable=true,require=1)",
            ),
        )
        assertTrue(
            method.contains(
                "private static void hookReceivingPacket(Packet<?> packet, PacketListener listener, CallbackInfo ci)",
            ),
        )
        method.assertInOrder(
            "ConnectionReceivingPacketHook.handle(",
            "packet",
            "listener",
            "ci::cancel",
            "packetInBundle -> genericsFtw(packetInBundle, listener)",
        )
        val handle = hook.method("public static void handle")
        val receiveBundle = hook.method("private static void receiveBundlePackets")
        val receiveSafely = hook.method("private static void receiveSafely")
        val dispatchIncoming = hook.method("private static void dispatchIncomingPacket")
        handle.assertInOrder(
            "packet instanceof ClientboundBundlePacket",
            "cancel.run()",
            "receiveBundlePackets(bundlePacket, receiver)",
            "return",
            "dispatchIncomingPacket(packet, cancel)",
        )
        receiveBundle.assertInOrder(
            "for (Packet<?> packetInBundle",
            "receiveSafely(packetInBundle, receiver)",
        )
        receiveSafely.assertInOrder(
            "receiver.accept(packet)",
            "catch (RunningOnDifferentThreadException ignored)",
        )
        dispatchIncoming.assertInOrder(
            "new PacketEvent(TransferOrigin.INCOMING, packet, true)",
            "EventManager.INSTANCE.callEvent(event)",
            "if (event.isCancelled())",
            "cancel.run()",
        )
    }

    @Test
    fun `camera orientation keeps cancellable target and applies camera work before cancel`() {
        val mixin = read(MIXIN_CAMERA)
        val method = mixin.method("private void modifyCameraOrientation")
        val hook = read(CAMERA_ORIENTATION_HOOK)

        assertTrue(
            mixin.compact().contains(
                "@Inject(method=\"alignWithEntity\",at=@At(value=\"INVOKE\"," +
                    "target=\"$SET_POSITION_DESCRIPTOR\",shift=At.Shift.AFTER),cancellable=true)",
            ),
        )
        assertTrue(method.contains("private void modifyCameraOrientation(float partialTicks, CallbackInfo ci)"))
        method.assertInOrder(
            "CameraOrientationHook.apply(",
            "this.entity",
            "this.minecraft",
            "() -> this.detached = true",
            "() -> this.yRot",
            "() -> this.xRot",
            "this::setRotation",
            "this::getMaxZoom",
            "this::move",
            "this::setPosition",
            "ci.cancel()",
        )
        hook.assertInOrder(
            "ModuleFreeLook.INSTANCE.getRunning()",
            "ModuleFreeLook.INSTANCE.isInvertedView()",
            "ModuleQuickPerspectiveSwap.INSTANCE.getRunning()",
            "isRearView(qps, freeLook, minecraft)",
            "detached.run()",
            "applyFreeLook",
            "applyQuickPerspectiveSwap",
            "moveCamera",
            "return true",
            "applyDroneCamera",
            "return false",
        )
        hook.assertInOrder(
            "setPosition.accept(screen.getCameraPos())",
            "setRotation.set(screen.getCameraRotation().yRot(), screen.getCameraRotation().xRot())",
        )
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
        const val MIXIN_LOCAL_PLAYER =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/entity/MixinLocalPlayer.java"
        const val MIXIN_ITEM_IN_HAND_RENDERER =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/item/MixinItemInHandRenderer.java"
        const val MIXIN_CONNECTION =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/network/MixinConnection.java"
        const val MIXIN_CAMERA =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinCamera.java"
        const val LOCAL_PLAYER_RAYCAST_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/LocalPlayerRaycastHook.java"
        const val FIRST_PERSON_ITEM_TRANSFORM_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/FirstPersonItemTransformHook.java"
        const val CONNECTION_RECEIVING_PACKET_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/ConnectionReceivingPacketHook.java"
        const val CAMERA_ORIENTATION_HOOK =
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/CameraOrientationHook.java"
        const val PICK_DESCRIPTOR =
            "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;"
        const val ENTITY_PICK_DESCRIPTOR =
            "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
        const val PUSH_POSE_DESCRIPTOR = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"
        const val SET_POSITION_DESCRIPTOR = "Lnet/minecraft/client/Camera;setPosition(DDD)V"
    }
}
