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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.fastutil.objectLinkedSetOf
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.AllowAutoJumpEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.ModuleToggleEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.entity.doesCollideAt
import net.ccbluex.liquidbounce.utils.entity.doesNotCollideBelow
import net.ccbluex.liquidbounce.utils.entity.getMovementDirectionOfInput
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.CRITICAL_MODIFICATION
import net.ccbluex.liquidbounce.utils.math.fma
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.getDegreesRelativeToView
import net.ccbluex.liquidbounce.utils.movement.getDirectionalInputForDegrees
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

/** Owns autonomous target selection and movement while KillAura owns all attack execution. */
@Suppress("TooManyFunctions")
object ModuleFightBot : ClientModule("FightBot", ModuleCategories.COMBAT) {

    private val opponentRange by float("OpponentRange", 3f, 0.1f..10f)
    private val dangerousYawDiff by float("DangerousYaw", 55f, 0f..90f, suffix = "°")
    private val runawayOnCooldown by boolean("RunawayOnCooldown", true)
    private val autoEnableKillAura by boolean("AutoEnableKillAura", true)
    private val spearAutomation by enumChoice("SpearAutomation", FightBotSpearAutomation.HeldOrHotbar)
    private val maceAutomation by enumChoice("MaceAutomation", FightBotMaceAutomation.Off)
    private val autoAction by multiEnumChoice("Auto", AutoAction.entries)

    private val targetTracker = tree(FightBotTargetTracker())

    private object LeaderFollower : net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup(
        ModuleFightBot,
        "Leader",
        false,
    ) {
        val username by text("Username", "")
        val radius by float("Radius", 5f, 2f..10f)
    }

    private var killAuraLease = FightBotKillAuraLease.start(autoEnable = false, killAuraEnabled = false)
    private var currentTargetHandoff: FightBotTargetHandoff = FightBotTargetHandoff.Idle

    init {
        tree(LeaderFollower)
    }

    internal val targetHandoff: FightBotTargetHandoff
        get() = if (running) currentTargetHandoff else FightBotTargetHandoff.Inactive

    internal val configuredSpearAutomation: FightBotSpearAutomation
        get() = spearAutomation

    internal val configuredMaceAutomation: FightBotMaceAutomation
        get() = maceAutomation

    private val combatOperational: Boolean
        get() = killAuraLease.isOperational(ModuleKillAura.running)

    override fun onEnabled() {
        killAuraLease = FightBotKillAuraLease.start(autoEnableKillAura, ModuleKillAura.enabled)
        if (killAuraLease.enableKillAura) {
            ModuleKillAura.enabled = true
        }
        currentTargetHandoff = FightBotTargetHandoff.Idle
    }

    override fun onDisabled() {
        val disableLeasedKillAura = killAuraLease.shouldDisableKillAuraOnRelease
        clearTargetAndWeapons(SpearKillFightBotTerminal.Disable, MaceKillFightBotTerminal.Disable)
        killAuraLease = FightBotKillAuraLease.start(autoEnable = false, killAuraEnabled = false)
        if (disableLeasedKillAura && ModuleKillAura.enabled) {
            ModuleKillAura.enabled = false
        }
    }

    @Suppress("unused")
    private val killAuraToggleHandler = handler<ModuleToggleEvent> { event ->
        if (!event.moduleName.equals(ModuleKillAura.name, ignoreCase = true) || event.enabled) return@handler

        killAuraLease = killAuraLease.onKillAuraDisabled()
        clearTargetAndWeapons(SpearKillFightBotTerminal.TargetLoss, MaceKillFightBotTerminal.TargetLoss)
    }

    @Suppress("unused")
    private val targetUpdateHandler = handler<RotationUpdateEvent> {
        if (!combatOperational || player.isDeadOrDying || player.isSpectator) {
            val spearTerminal = if (player.isDeadOrDying) {
                SpearKillFightBotTerminal.Death
            } else {
                SpearKillFightBotTerminal.TargetLoss
            }
            val maceTerminal = if (player.isDeadOrDying) {
                MaceKillFightBotTerminal.Death
            } else {
                MaceKillFightBotTerminal.TargetLoss
            }
            clearTargetAndWeapons(spearTerminal, maceTerminal)
            return@handler
        }

        updateTarget()
        val target = targetTracker.target
        currentTargetHandoff = target?.let(FightBotTargetHandoff::Locked) ?: FightBotTargetHandoff.Idle

        if (target == null || target.squaredBoxedDistanceTo(player) <= ModuleKillAura.extendedInteractionRange.sq()) {
            ModuleSpearKill.releaseFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
            ModuleMaceKill.releaseFightBotMaceUse(MaceKillFightBotTerminal.TargetLoss)
            return@handler
        }

        requestFightBotRemoteWeaponUse(target)
    }

    private fun updateTarget() {
        val routeTarget = selectFightBotRouteTarget(
            maceRouteTarget = ModuleMaceKill.fightBotRouteTarget,
            spearRouteTarget = ModuleSpearKill.fightBotRouteTarget,
        )
        if (routeTarget != null && targetTracker.validate(routeTarget)) {
            targetTracker.target = routeTarget
            return
        }

        val candidates = targetTracker.targets()
        targetTracker.target = selectFightBotTarget(
            mode = targetTracker.mode,
            configuredName = targetTracker.configuredName,
            candidates = candidates,
            nameOf = { (it as? Player)?.gameProfile?.name },
            distanceOf = { player.squaredBoxedDistanceTo(it) },
            isEligible = { true },
        )
    }

    private fun requestFightBotRemoteWeaponUse(target: LivingEntity) {
        val maceSource = resolveFightBotMaceUseSource()
        val spearSource = resolveFightBotSpearUseSource()
        when (selectFightBotRemoteWeapon(
            maceSource = maceSource,
            spearSource = spearSource,
            maceRouteActive = ModuleMaceKill.fightBotStateFor(target) == MaceKillFightBotState.RouteActive,
            spearRouteActive = ModuleSpearKill.fightBotStateFor(target) == SpearKillFightBotState.RouteActive,
        )) {
            FightBotRemoteWeapon.Mace -> {
                ModuleSpearKill.releaseFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
                if (!ModuleMaceKill.fightBotStateFor(target).retainsRejectedTarget) {
                    ModuleMaceKill.requestFightBotMaceUse(target)
                }
            }
            FightBotRemoteWeapon.Spear -> {
                ModuleMaceKill.releaseFightBotMaceUse(MaceKillFightBotTerminal.TargetLoss)
                ModuleSpearKill.requestFightBotSpearUse(target)
            }
            null -> {
                ModuleMaceKill.releaseFightBotMaceUse(MaceKillFightBotTerminal.TargetLoss)
                ModuleSpearKill.releaseFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
            }
        }
    }

    private fun resolveFightBotMaceUseSource(): FightBotMaceUseSource? {
        if (!ModuleMaceKill.running) return null
        return selectFightBotMaceUseSource(
            automation = maceAutomation,
            mainHandMace = player.mainHandItem.item === Items.MACE,
            selectedHotbarSlot = SilentHotbar.serversideSlot,
            hotbarMaceSlots = Slots.Hotbar.asSequence()
                .filter { it.itemStack.item === Items.MACE }
                .mapNotNull { it.hotbarIndex }
                .toList(),
        )
    }

    private fun resolveFightBotSpearUseSource(): FightBotSpearUseSource? {
        if (!ModuleSpearKill.running) return null
        return selectFightBotSpearUseSource(
            automation = spearAutomation,
            mainHandSpear = player.mainHandItem.isSpear,
            offhandSpear = player.offhandItem.isSpear,
            selectedHotbarSlot = SilentHotbar.serversideSlot,
            hotbarSpearSlots = Slots.Hotbar.asSequence()
                .filter { it.itemStack.isSpear }
                .mapNotNull { it.hotbarIndex }
                .toList(),
        )
    }

    private fun clearTargetAndWeapons(
        spearTerminal: SpearKillFightBotTerminal,
        maceTerminal: MaceKillFightBotTerminal,
    ) {
        targetTracker.reset()
        currentTargetHandoff = FightBotTargetHandoff.Idle
        ModuleSpearKill.releaseFightBotSpearUse(spearTerminal)
        ModuleMaceKill.releaseFightBotMaceUse(maceTerminal)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        clearTargetAndWeapons(SpearKillFightBotTerminal.WorldChange, MaceKillFightBotTerminal.WorldChange)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        clearTargetAndWeapons(SpearKillFightBotTerminal.Disconnect, MaceKillFightBotTerminal.Disconnect)
    }

    @Suppress("unused")
    private val inputHandler = handler<MovementInputEvent>(priority = CRITICAL_MODIFICATION) { event ->
        if (!combatOperational) return@handler
        val context = createNavigationContext()
        val goal = calculateGoalPosition(context) ?: return@handler

        ModuleDebug.debugGeometry(this, "Goal", ModuleDebug.DebuggedPoint(goal, Color4b.BLUE, size = 0.4))
        event.directionalInput = calculateDirectionalInput(event.directionalInput, goal)
        handleMovementAssist(event, context)
    }

    @Suppress("unused")
    private val sprintHandler = handler<SprintEvent>(priority = CRITICAL_MODIFICATION) { event ->
        if (!combatOperational || AutoAction.SPRINT !in autoAction || !event.directionalInput.isMoving) {
            return@handler
        }
        if (event.source == SprintEvent.Source.MOVEMENT_TICK || event.source == SprintEvent.Source.INPUT) {
            event.sprint = true
        }
    }

    @Suppress("unused")
    private val autoJumpHandler = handler<AllowAutoJumpEvent> { event ->
        if (combatOperational && AutoAction.JUMP in autoAction) event.isAllowed = true
    }

    private fun createNavigationContext(): FightBotCombatContext {
        val playerPosition = player.position()
        val combatTarget = targetTracker.target?.let { entity ->
            val distance = playerPosition.distanceTo(entity.position())
            val range = min(ModuleKillAura.range.interactionRange, distance.toFloat())
            val targetRotation = entity.rotation.copy(pitch = 0.0f)
            val requiredRotation = Rotation.lookingAt(playerPosition, entity.eyePosition).copy(pitch = 0.0f)
            FightBotCombatTarget(
                entity = entity,
                range = range,
                outOfDistance = distance > opponentRange,
                targetRotation = targetRotation,
                requiredTargetRotation = requiredRotation,
                outOfDanger = abs(targetRotation.rotationDeltaTo(requiredRotation).deltaYaw) > dangerousYawDiff,
            )
        }
        return FightBotCombatContext(playerPosition, combatTarget)
    }

    private fun calculateGoalPosition(context: FightBotCombatContext): Vec3? {
        if (LeaderFollower.running && LeaderFollower.username.isNotEmpty()) {
            world.players().find { it.gameProfile.name == LeaderFollower.username }?.let { leader ->
                return calculateLeaderGoalPosition(leader.position(), context.playerPosition)
            }
        }

        val combatTarget = context.combatTarget ?: return null
        return if (runawayOnCooldown && !ModuleKillAura.clicker.willClickAt()) {
            context.playerPosition.fma(
                combatTarget.range.toDouble(),
                combatTarget.requiredTargetRotation.directionVector,
            )
        } else {
            calculateAttackPosition(context, combatTarget)
        }
    }

    private fun handleMovementAssist(event: MovementInputEvent, context: FightBotCombatContext) {
        if ((AutoAction.SWIM in autoAction && player.isInWater) ||
            (AutoAction.JUMP in autoAction && player.horizontalCollision)
        ) {
            event.jump = true
        }

        val targetAllowsJump = context.combatTarget?.let { it.outOfDistance && !it.outOfDanger } == true
        val goal = calculateGoalPosition(context) ?: return
        val leaderAllowsJump = LeaderFollower.running && player.position().distanceTo(goal) > LeaderFollower.radius
        if (targetAllowsJump || leaderAllowsJump) event.jump = true
    }

    internal fun getMovementRotation(): Rotation {
        val movementRotation = Rotation(player.getMovementDirectionOfInput(), 0.0f)
        val movementPitch = targetTracker.target?.let { entity ->
            Rotation.lookingAt(point = entity.boundingBox.center, from = player.eyePosition).pitch
        } ?: return movementRotation
        return movementRotation.copy(pitch = movementPitch)
    }

    private fun calculateDirectionalInput(currentInput: DirectionalInput, goal: Vec3): DirectionalInput {
        val degrees = getDegreesRelativeToView(goal.subtract(player.position()), player.yRot)
        return getDirectionalInputForDegrees(currentInput, degrees, deadAngle = 20.0F)
    }

    private fun calculateLeaderGoalPosition(leaderPosition: Vec3, playerPosition: Vec3): Vec3 =
        (-180..180 step 45).map { yaw ->
            val position = leaderPosition.fma(
                LeaderFollower.radius.toDouble(),
                Rotation(yaw.toFloat(), 0.0F).directionVector,
            )
            ModuleDebug.debugGeometry(
                this,
                "Possible Position $yaw",
                ModuleDebug.DebuggedPoint(position, Color4b.MAGENTA),
            )
            position
        }.minByOrNull { it.distanceToSqr(playerPosition) } ?: leaderPosition

    private fun calculateAttackPosition(
        context: FightBotCombatContext,
        combatTarget: FightBotCombatTarget,
    ): Vec3 {
        val target = combatTarget.entity
        val targetLookPosition = target.position().fma(
            combatTarget.range.toDouble(),
            combatTarget.targetRotation.directionVector,
        )

        return (-180..180 step 10).mapNotNull { yaw ->
            val rotation = Rotation(yaw.toFloat(), 0.0F)
            val position = target.position().fma(combatTarget.range.toDouble(), rotation.directionVector)
            if (player.doesCollideAt(position)) return@mapNotNull null

            val dangerous = abs(rotation.rotationDeltaTo(combatTarget.targetRotation).deltaYaw) <= dangerousYawDiff
            ModuleDebug.debugGeometry(
                this,
                "Possible Position $yaw",
                ModuleDebug.DebuggedPoint(position, if (dangerous) Color4b.RED else Color4b.GREEN),
            )
            position.takeUnless { dangerous }
        }.sortedBy { it.distanceToSqr(targetLookPosition) }
            .minByOrNull { it.distanceToSqr(context.playerPosition) }
            ?: targetLookPosition
    }

    private class FightBotTargetTracker : TargetTracker(
        fovRange = 0f..365f,
        defaultPriorities = objectLinkedSetOf(TargetPriority.DISTANCE),
    ) {
        val mode by enumChoice("Mode", FightBotTargetMode.Nearest)
        val configuredName by text("Name", "")
        private val range by float("Range", 50f, 10f..100f)
        private val visibleOnly by boolean("VisibleOnly", true)
        private val notWhenVoid by boolean("NotWhenVoid", true)

        override fun validate(entity: LivingEntity): Boolean = super.validate(entity) &&
            entity.isAlive &&
            player.squaredBoxedDistanceTo(entity) <= range.sq() &&
            (!visibleOnly || !entity.isInvisible && player.hasLineOfSight(entity)) &&
            (!notWhenVoid || !entity.doesNotCollideBelow())
    }

    private enum class AutoAction(override val tag: String) : Tagged {
        JUMP("Jump"),
        SWIM("Swim"),
        SPRINT("Sprint"),
    }
}

private data class FightBotCombatContext(
    val playerPosition: Vec3,
    val combatTarget: FightBotCombatTarget?,
)

private data class FightBotCombatTarget(
    val entity: LivingEntity,
    val range: Float,
    val outOfDistance: Boolean,
    val targetRotation: Rotation,
    val requiredTargetRotation: Rotation,
    val outOfDanger: Boolean,
)
