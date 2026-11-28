// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import dev.thunderid.android.AuthenticationChallenge
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StepUpTest {
    private val stepUp =
        IAMException(
            ThunderIDErrorCode.STEP_UP_REQUIRED,
            "Recent authentication required",
            authenticationChallenge = AuthenticationChallenge(error = "insufficient_user_authentication", maxAge = 60),
        )

    private suspend fun failure(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    @Test
    fun `retries once after a successful re-sign-in with the challenge's max age`() =
        runTest {
            var calls = 0
            var requested: Int? = null
            val reauthenticate: Reauthenticator = { maxAge ->
                requested = maxAge
                true
            }

            val result =
                withStepUp(reauthenticate) {
                    calls++
                    if (calls == 1) throw stepUp
                    "saved"
                }

            assertEquals("saved", result)
            assertEquals(2, calls)
            assertEquals(60, requested)
        }

    @Test
    fun `a second refusal is thrown with no third attempt`() =
        runTest {
            var calls = 0
            var signIns = 0
            val reauthenticate: Reauthenticator = {
                signIns++
                true
            }

            val error =
                failure {
                    withStepUp(reauthenticate) {
                        calls++
                        throw stepUp
                    }
                }

            assertSame(stepUp, error)
            assertEquals(2, calls)
            assertEquals(1, signIns)
        }

    @Test
    fun `a failed re-sign-in throws the refusal without retrying`() =
        runTest {
            var calls = 0

            val error =
                failure {
                    withStepUp({ false }) {
                        calls++
                        throw stepUp
                    }
                }

            assertSame(stepUp, error)
            assertEquals(1, calls)
        }

    @Test
    fun `other failures, and step-up refusals with no re-sign-in, are thrown as they are`() =
        runTest {
            val forbidden = IAMException(ThunderIDErrorCode.FORBIDDEN, "Forbidden")
            var signIns = 0
            val reauthenticate: Reauthenticator = {
                signIns++
                true
            }

            assertSame(forbidden, failure { withStepUp(reauthenticate) { throw forbidden } })
            assertSame(stepUp, failure { withStepUp(null) { throw stepUp } })
            assertEquals(0, signIns)
        }
}
