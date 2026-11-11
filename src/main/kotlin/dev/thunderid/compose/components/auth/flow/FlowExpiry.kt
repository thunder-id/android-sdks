// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.auth.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.FlowStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks when an embedded flow execution expires, and starts the flow again once it has and the screen is resumed.
 * A flow that expires while the app is in the background starts again when the user comes back to it.
 */
internal class FlowExpiry(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Starts the flow again. */
    var restart: () -> Unit = {}

    private var deadline: Long? = null
    private var isExpired = false
    private var isActive = true
    private var timer: Job? = null

    /**
     * Tracks the expiry of a step, counted from when its response arrived. A flow that ended, or a step without an
     * expiry, stops the tracking.
     */
    fun track(response: EmbeddedFlowResponse) {
        stop()
        val expiresIn = response.expiresIn ?: return
        if (response.flowStatus == FlowStatus.COMPLETE || response.flowStatus == FlowStatus.ERROR) return
        val delayMillis = expiresIn.coerceAtLeast(0) * MILLIS_PER_SECOND
        deadline = now() + delayMillis
        timer =
            scope.launch {
                delay(delayMillis)
                expire()
            }
    }

    /** Marks the flow as expired, e.g. when the server refused a step as expired. */
    fun expire() {
        stop()
        isExpired = true
        restartIfActive()
    }

    /** Tells whether the screen is resumed. Coming back checks the deadline again, since the timer may not have run. */
    fun setActive(active: Boolean) {
        isActive = active
        val due = deadline
        if (due != null && now() >= due) expire() else restartIfActive()
    }

    /** Stops tracking the current flow. */
    fun stop() {
        timer?.cancel()
        timer = null
        deadline = null
        isExpired = false
    }

    private fun restartIfActive() {
        if (!isExpired || !isActive) return
        isExpired = false
        restart()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

/** A [FlowExpiry] that follows the screen's lifecycle, and starts the flow again through [restart]. */
@Composable
internal fun rememberFlowExpiry(restart: () -> Unit): FlowExpiry {
    val scope = rememberCoroutineScope()
    val expiry = remember { FlowExpiry(scope) }
    expiry.restart = restart
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> expiry.setActive(true)
                    Lifecycle.Event.ON_PAUSE -> expiry.setActive(false)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            expiry.stop()
        }
    }
    return expiry
}
