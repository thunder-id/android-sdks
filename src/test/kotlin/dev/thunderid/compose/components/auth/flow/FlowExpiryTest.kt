// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.auth.flow

import com.google.gson.Gson
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.compose.components.auth.SignInState
import dev.thunderid.compose.components.auth.SignUpState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowExpiryTest {
    private var restarts = 0

    private fun step(
        status: String,
        expiresIn: Long?,
    ): EmbeddedFlowResponse {
        val expiry = expiresIn?.let { ""","expiresIn":$it""" } ?: ""
        return Gson().fromJson("""{"executionId":"f-1","flowStatus":"$status"$expiry}""", EmbeddedFlowResponse::class.java)
    }

    private fun TestScope.flowExpiry(clock: () -> Long = { testScheduler.currentTime }): FlowExpiry =
        FlowExpiry(backgroundScope, clock).also { it.restart = { restarts++ } }

    @Test
    fun `starts again once the step expires`() =
        runTest {
            val expiry = flowExpiry()
            expiry.track(step("INCOMPLETE", 60))

            advanceTimeBy(59_999)
            runCurrent()
            assertEquals(0, restarts)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, restarts)
        }

    @Test
    fun `starts again when the server refuses the step as expired`() =
        runTest {
            flowExpiry().expire()
            assertEquals(1, restarts)
        }

    @Test
    fun `waits for the screen to be resumed before starting again`() =
        runTest {
            val expiry = flowExpiry()
            expiry.setActive(false)
            expiry.expire()
            assertEquals(0, restarts)

            expiry.setActive(true)
            assertEquals(1, restarts)
            expiry.setActive(true)
            assertEquals("starts again only once", 1, restarts)
        }

    @Test
    fun `checks the deadline when the screen is resumed again`() =
        runTest {
            var now = 0L
            val expiry = flowExpiry { now }
            expiry.track(step("INCOMPLETE", 60))
            expiry.setActive(false)

            // The clock moves past the deadline without the timer firing, as in the background.
            now = 61_000
            expiry.setActive(true)

            assertEquals(1, restarts)
        }

    @Test
    fun `stops tracking a flow that ended`() =
        runTest {
            val expiry = flowExpiry()
            expiry.track(step("INCOMPLETE", 60))
            expiry.track(step("COMPLETE", null))

            advanceTimeBy(61_000)
            runCurrent()

            assertEquals(0, restarts)
        }

    @Test
    fun `sign-in state notes the restart until the user acts`() =
        runTest {
            val state = SignInState()
            var submitted: String? = null
            state.onSubmit = { submitted = it }

            state.prepareRestart()
            assertTrue(state.isFlowRestarted)
            assertTrue(state.components.isEmpty())

            state.submit("submit")
            assertFalse(state.isFlowRestarted)
            assertEquals("submit", submitted)
        }

    @Test
    fun `sign-up state starts the flow again for a step refused as expired`() =
        runTest {
            val state = SignUpState()
            state.flowExpiry = flowExpiry()

            assertTrue(state.expireIfFlowExpired(IAMException(ThunderIDErrorCode.FLOW_EXPIRED, "expired")))
            assertEquals(1, restarts)
            assertFalse(state.expireIfFlowExpired(IAMException(ThunderIDErrorCode.INVALID_INPUT, "invalid")))
            assertEquals(1, restarts)
        }
}
