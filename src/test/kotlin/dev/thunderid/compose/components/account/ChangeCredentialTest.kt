// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import android.content.Context
import dev.thunderid.android.AuthenticationChallenge
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ServerError
import dev.thunderid.android.ServerErrorMessage
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpConfig
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.token.TokenStore
import dev.thunderid.compose.ThunderIDState
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeCredentialTest {
    // ── evaluateCredentialForm ──────────────────────────────────────────────

    @Test
    fun `evaluateCredentialForm is invalid when the new value is empty`() {
        val result = evaluateCredentialForm(newValue = "", confirm = "", regex = null)

        assertFalse(result.isValid)
    }

    @Test
    fun `evaluateCredentialForm is invalid when confirm does not match`() {
        val result = evaluateCredentialForm(newValue = "n3wValue!", confirm = "typo", regex = null)

        assertFalse(result.confirmMatches)
        assertFalse(result.isValid)
    }

    @Test
    fun `evaluateCredentialForm is valid with no policy`() {
        val result = evaluateCredentialForm(newValue = "n3wValue!", confirm = "n3wValue!", regex = null)

        assertFalse(result.patternChecked)
        assertTrue(result.isValid)
    }

    @Test
    fun `evaluateCredentialForm applies a regex policy`() {
        val short = evaluateCredentialForm(newValue = "short", confirm = "short", regex = "^.{8,}$")
        assertTrue(short.patternChecked)
        assertFalse(short.patternPassed)
        assertFalse(short.isValid)

        val long = evaluateCredentialForm(newValue = "longEnough1", confirm = "longEnough1", regex = "^.{8,}$")
        assertTrue(long.patternPassed)
        assertTrue(long.isValid)
    }

    @Test
    fun `evaluateCredentialForm does not need a current value`() {
        val state = ChangeCredentialState("Password")
        state.newValue = "n3wValue!"
        state.confirmValue = "n3wValue!"

        assertEquals("", state.currentValue)
        assertTrue(state.evaluation.isValid)
    }

    @Test
    fun `evaluateCredentialForm treats an uncompilable regex as passing`() {
        val result = evaluateCredentialForm(newValue = "anything", confirm = "anything", regex = "([")

        assertTrue(result.patternPassed)
        assertTrue(result.isValid)
    }

    // ── mapCredentialError ──────────────────────────────────────────────────

    @Test
    fun `mapCredentialError routes a bad request to the new field`() {
        assertEquals(CredentialField.NEW, mapCredentialError(ThunderIDErrorCode.INVALID_INPUT))
    }

    @Test
    fun `mapCredentialError routes other failures to form level`() {
        assertEquals(CredentialField.FORM, mapCredentialError(ThunderIDErrorCode.SERVER_ERROR))
    }

    @Test
    fun `mapCredentialError routes the current-value codes to the current field`() {
        assertEquals(CredentialField.CURRENT, mapCredentialError(ThunderIDErrorCode.INVALID_INPUT, "USR-1047"))
        assertEquals(CredentialField.CURRENT, mapCredentialError(ThunderIDErrorCode.FORBIDDEN, "USR-1048"))
        assertEquals(CredentialField.FORM, mapCredentialError(ThunderIDErrorCode.STEP_UP_REQUIRED, "AUTH-4011"))
        assertEquals(CredentialField.NEW, mapCredentialError(ThunderIDErrorCode.INVALID_INPUT, "USR-1017"))
    }

    // ── resolveCredentialError ──────────────────────────────────────────────

    private fun serverFailure(
        code: ThunderIDErrorCode,
        serverCode: String,
        message: String? = null,
        challenge: AuthenticationChallenge? = null,
    ) = IAMException(
        code,
        message ?: serverCode,
        serverError = ServerError(serverCode, message?.let { ServerErrorMessage(defaultValue = it) }),
        authenticationChallenge = challenge,
    )

    private fun resolve(error: IAMException) = resolveCredentialError(error) { key -> "t:$key" }

    @Test
    fun `another account signing in again shows the different-account message on the form`() {
        val error = resolve(dev.thunderid.android.AnotherAccountSignedInException())

        assertEquals(CredentialField.FORM, error.field)
        assertEquals("t:reauthentication.differentUser", error.message)
    }

    @Test
    fun `resolveCredentialError puts a missing current value on the current field`() {
        val resolved = resolve(serverFailure(ThunderIDErrorCode.INVALID_INPUT, "USR-1047", "Current credential required"))

        assertEquals(CredentialError(CredentialField.CURRENT, "t:changeCredential.current.required.error"), resolved)
    }

    @Test
    fun `resolveCredentialError puts a wrong current value on the current field`() {
        val resolved = resolve(serverFailure(ThunderIDErrorCode.FORBIDDEN, "USR-1048", "Invalid current credential"))

        assertEquals(CredentialError(CredentialField.CURRENT, "t:changeCredential.current.invalid.error"), resolved)
    }

    @Test
    fun `resolveCredentialError asks for a sign-in again with the challenge's max age`() {
        val challenge = AuthenticationChallenge(error = "insufficient_user_authentication", maxAge = 300)
        val resolved = resolve(serverFailure(ThunderIDErrorCode.STEP_UP_REQUIRED, "AUTH-4011", challenge = challenge))

        assertEquals(CredentialField.FORM, resolved.field)
        assertEquals("t:changeCredential.reauthentication.error", resolved.message)
        assertTrue(resolved.reauthenticationRequired)
        assertEquals(300, resolved.maxAge)
    }

    @Test
    fun `resolveCredentialError asks for a sign-in again on AUTH-4011 without a max age`() {
        val resolved = resolve(serverFailure(ThunderIDErrorCode.STEP_UP_REQUIRED, "AUTH-4011"))

        assertTrue(resolved.reauthenticationRequired)
        assertNull(resolved.maxAge)
    }

    @Test
    fun `resolveCredentialError shows the server's message for other refusals, else the generic text`() {
        val refused = resolve(serverFailure(ThunderIDErrorCode.INVALID_INPUT, "USR-1017", "Missing credentials"))
        assertEquals(CredentialError(CredentialField.NEW, "Missing credentials"), refused)
        assertFalse(refused.reauthenticationRequired)

        val bare = resolve(IAMException(ThunderIDErrorCode.SERVER_ERROR, "Server error: 500"))
        assertEquals(CredentialError(CredentialField.FORM, "t:changeCredential.generic.error"), bare)
    }

    // ── send ────────────────────────────────────────────────────────────────

    private class RecordingFetcher(
        vararg answers: ThunderIDHttpResponse,
    ) : ThunderIDFetcher {
        private val queue = answers.toMutableList()
        val bodies = mutableListOf<JSONObject>()

        override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
            bodies += JSONObject(request.body!!)
            return if (queue.size > 1) queue.removeAt(0) else queue.firstOrNull() ?: ThunderIDHttpResponse(204, "")
        }
    }

    private val stepUp = ThunderIDHttpResponse(401, """{"code":"AUTH-4011","description":{"params":{"maxAge":"300"}}}""")

    private suspend fun signedInClient(fetcher: ThunderIDFetcher): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "user-token", tokenType = "Bearer", expiresIn = 3600))
        return ThunderIDClient(fetcher).apply {
            initialize(
                ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "client"),
                storage,
            )
        }
    }

    @Test
    fun `send includes the current value only when the user filled it in`() =
        runTest {
            val fetcher = RecordingFetcher()
            val client = signedInClient(fetcher)
            val state = ChangeCredentialState("Password")
            state.newValue = "n3wValue!"
            state.confirmValue = "n3wValue!"

            state.request(client, "password")()
            state.currentValue = "0ldValue!"
            state.request(client, "password")()

            val (first, second) = fetcher.bodies
            assertEquals(setOf("name", "newValue"), first.keySet())
            assertEquals(setOf("name", "currentValue", "newValue"), second.keySet())
            assertEquals("0ldValue!", second.getString("currentValue"))
            assertEquals("password", second.getString("name"))
        }

    // ── Step-up retry ───────────────────────────────────────────────────────

    private fun filledState() =
        ChangeCredentialState("Password").apply {
            newValue = "n3wValue!"
            confirmValue = "n3wValue!"
        }

    @Test
    fun `a step-up refusal signs in again with the max age and sends the same change once more`() =
        runTest {
            val fetcher = RecordingFetcher(stepUp, ThunderIDHttpResponse(204, ""))
            val client = signedInClient(fetcher)
            val state = filledState()
            val maxAges = mutableListOf<Int?>()

            val reauthenticate: Reauthenticator = { maxAge ->
                maxAges += maxAge
                true
            }

            withStepUp(reauthenticate, state.request(client, "password"))

            assertEquals(listOf<Int?>(300), maxAges)
            assertEquals(2, fetcher.bodies.size)
            assertEquals(fetcher.bodies[0].toString(), fetcher.bodies[1].toString())
            assertFalse(fetcher.bodies[1].has("currentValue"))
        }

    @Test
    fun `the retry after a re-sign-in sends the values the user submitted`() =
        runTest {
            val fetcher = RecordingFetcher(stepUp, ThunderIDHttpResponse(204, ""))
            val client = signedInClient(fetcher)
            val state = filledState()
            val reauthenticate: Reauthenticator = {
                state.newValue = "edited!"
                true
            }

            withStepUp(reauthenticate, state.request(client, "password"))

            assertEquals("n3wValue!", fetcher.bodies[1].getString("newValue"))
        }

    @Test
    fun `a second step-up refusal shows the sign-in-again message`() =
        runTest {
            val fetcher = RecordingFetcher(stepUp)
            val client = signedInClient(fetcher)
            val state = filledState()
            var signIns = 0
            val reauthenticate: Reauthenticator = {
                signIns++
                true
            }

            val error =
                runCatching { withStepUp(reauthenticate, state.request(client, "password")) }.exceptionOrNull() as IAMException

            assertEquals(1, signIns)
            assertEquals(2, fetcher.bodies.size)
            assertEquals(ThunderIDErrorCode.STEP_UP_REQUIRED, error.code)
            assertEquals(CredentialField.FORM, resolve(error).field)
            assertEquals("t:changeCredential.reauthentication.error", resolve(error).message)
        }

    @Test
    fun `a failed or cancelled re-sign-in sends nothing more and shows the sign-in-again message`() =
        runTest {
            val fetcher = RecordingFetcher(stepUp, ThunderIDHttpResponse(204, ""))
            val client = signedInClient(fetcher)

            val error =
                runCatching { withStepUp({ _ -> false }, filledState().request(client, "password")) }
                    .exceptionOrNull() as IAMException

            assertEquals(1, fetcher.bodies.size)
            assertEquals("t:changeCredential.reauthentication.error", resolve(error).message)
        }

    @Test
    fun `without a re-sign-in, as when the app handles it, the step-up refusal comes back with no retry`() =
        runTest {
            val fetcher = RecordingFetcher(stepUp, ThunderIDHttpResponse(204, ""))
            val client = signedInClient(fetcher)

            val error =
                runCatching { withStepUp(null, filledState().request(client, "password")) }.exceptionOrNull() as IAMException

            assertEquals(1, fetcher.bodies.size)
            assertTrue(resolve(error).reauthenticationRequired)
            assertEquals(300, resolve(error).maxAge)
        }

    @Test
    fun `an app with its own re-sign-in gets no built-in one`() =
        runTest {
            val stepUpState = StepUpState()
            val thunderState = mockk<ThunderIDState>()

            assertNull(stepUpState.reauthenticator(thunderState, mockk<Context>(), onReauthenticationRequired = {}))
        }

    // ── helpers ─────────────────────────────────────────────────────────────

    @Test
    fun `substituteCredential replaces both placeholders`() {
        assertEquals("Change PIN (pin)", substituteCredential("Change {credential} ({credentialLower})", "PIN"))
    }

    @Test
    fun `titleCaseCredential capitalizes the first letter`() {
        assertEquals("Pin", titleCaseCredential("pin"))
        assertEquals("Password", titleCaseCredential("password"))
    }
}
