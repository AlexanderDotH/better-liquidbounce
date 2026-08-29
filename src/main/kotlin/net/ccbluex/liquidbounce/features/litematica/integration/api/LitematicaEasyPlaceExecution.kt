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
package net.ccbluex.liquidbounce.features.litematica.integration.api

import java.util.concurrent.atomic.AtomicLong

class LitematicaEasyPlaceExecutionToken internal constructor(
    internal val sequence: Long,
    internal val ownerThread: Thread,
) : AutoCloseable {
    private var closed = false

    fun <T> runControlled(action: () -> T): T {
        check(!closed) { "Easy Place execution token is already closed" }
        return LitematicaEasyPlaceExecutionGate.runControlled(this, action)
    }

    override fun close() {
        if (closed) return
        LitematicaEasyPlaceExecutionGate.endOwnedExecution(this)
        closed = true
    }
}

interface LitematicaEasyPlaceOwnershipLease : AutoCloseable {
    val isActive: Boolean
    fun beginExecution(): LitematicaEasyPlaceExecutionToken
    override fun close()
}

/**
 * Thread-confined ownership gate shared by the module, bridge and optional mixin.
 * Native Easy Place entry points are suppressed while LiquidBounce owns the printer,
 * except for the single bridge invocation authorized by the returned token.
 */
object LitematicaEasyPlaceExecutionGate {
    private val sequences = AtomicLong()
    private val localState = ThreadLocal<State?>()

    @JvmStatic
    fun acquirePrinterOwnership(): LitematicaEasyPlaceOwnershipLease {
        localState.get()?.let { return it.lease }
        val lease = OwnershipLease(Thread.currentThread())
        localState.set(State(lease))
        return lease
    }

    @JvmStatic
    fun shouldSuppressNativeEasyPlace(): Boolean {
        val state = localState.get() ?: return false
        return state.lease.isActive && !state.controlledInvocation
    }

    private fun beginOwnedExecution(lease: OwnershipLease): LitematicaEasyPlaceExecutionToken {
        val state = requireState(lease)
        check(state.token == null) { "An Easy Place action is already active on this thread" }
        state.consumed = false
        return LitematicaEasyPlaceExecutionToken(sequences.incrementAndGet(), Thread.currentThread()).also {
            state.token = it
        }
    }

    internal fun <T> runControlled(token: LitematicaEasyPlaceExecutionToken, action: () -> T): T {
        val state = requireState(token)
        check(!state.consumed) { "Easy Place execution token was already consumed" }
        state.consumed = true
        state.controlledInvocation = true
        return try {
            action()
        } finally {
            state.controlledInvocation = false
        }
    }

    internal fun endOwnedExecution(token: LitematicaEasyPlaceExecutionToken) {
        val state = requireState(token)
        state.token = null
    }

    internal fun resetForTests() {
        localState.remove()
    }

    private fun requireState(token: LitematicaEasyPlaceExecutionToken): State {
        check(token.ownerThread === Thread.currentThread()) {
            "Easy Place execution token must stay on its owner thread"
        }
        val state = checkNotNull(localState.get()) { "No owned Easy Place action is active" }
        check(state.token?.sequence == token.sequence) { "Easy Place execution token does not own this action" }
        return state
    }

    private fun requireState(lease: OwnershipLease): State {
        check(lease.ownerThread === Thread.currentThread()) {
            "Easy Place ownership must stay on its owner thread"
        }
        val state = checkNotNull(localState.get()) { "No Easy Place ownership is active" }
        check(state.lease === lease && lease.isActive) { "Easy Place ownership lease is not active" }
        return state
    }

    private class State(
        val lease: OwnershipLease,
        var token: LitematicaEasyPlaceExecutionToken? = null,
        var consumed: Boolean = false,
        var controlledInvocation: Boolean = false,
    )

    private class OwnershipLease(
        val ownerThread: Thread,
    ) : LitematicaEasyPlaceOwnershipLease {
        override var isActive: Boolean = true
            private set

        override fun beginExecution(): LitematicaEasyPlaceExecutionToken {
            check(isActive) { "Easy Place ownership lease is already closed" }
            return LitematicaEasyPlaceExecutionGate.beginOwnedExecution(this)
        }

        override fun close() {
            if (!isActive) return
            val state = LitematicaEasyPlaceExecutionGate.requireState(this)
            check(state.token == null) { "Cannot release Easy Place ownership during an active action" }
            isActive = false
            LitematicaEasyPlaceExecutionGate.localState.remove()
        }
    }
}
