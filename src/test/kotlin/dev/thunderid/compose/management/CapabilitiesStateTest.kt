// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.User
import dev.thunderid.android.api.management.Capabilities
import dev.thunderid.android.api.management.CapabilityScope
import dev.thunderid.android.api.management.RecordAccess
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.management.CapabilityGuardOutcome
import dev.thunderid.compose.components.management.capabilityGuardOutcome
import dev.thunderid.compose.components.management.capabilityResource
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private val ALL_ALLOWED =
    Capabilities(
        CapabilityScope("deployment"),
        mapOf(
            "user" to mapOf("view" to true, "create" to true, "update" to true, "delete" to true),
            "app" to mapOf("view" to true, "create" to false),
        ),
    )

/** A fake server: counts reads and answers each with [answer], or waits on [gate] when set. */
private class FakeServer {
    var reads = 0
    var answer: () -> Capabilities = { ALL_ALLOWED }
    var gate: CompletableDeferred<Unit>? = null
    var permissions = listOf("system")

    suspend fun read(): Capabilities {
        reads++
        gate?.await()
        return answer()
    }
}

class CapabilitiesStateTest {
    private fun TestScope.newState(server: FakeServer) =
        CapabilitiesState(this, read = { server.read() }, grantedPermissions = { server.permissions })

    private val alice = User(mapOf("sub" to "alice", "ouId" to "ou-1"))

    @Test
    fun `nothing is read until load, and not while signed out`() =
        runTest {
            val server = FakeServer()
            val state = newState(server)
            state.setUser(alice)
            advanceUntilIdle()
            assertEquals(0, server.reads)

            val signedOut = newState(server)
            signedOut.load()
            advanceUntilIdle()
            assertEquals(0, server.reads)
            assertFalse(signedOut.can("user"))
        }

    @Test
    fun `can is false while loading, for unreported entries, and defaults to view`() =
        runTest {
            val server = FakeServer().apply { gate = CompletableDeferred() }
            val state = newState(server)
            state.setUser(alice)
            state.load()

            assertTrue(state.isLoading)
            assertFalse(state.can("user"))

            server.gate!!.complete(Unit)
            advanceUntilIdle()

            assertFalse(state.isLoading)
            assertTrue(state.can("user"))
            assertTrue(state.can("user", "view"))
            assertTrue(state.can("user", "delete"))
            assertFalse(state.can("app", "create"))
            assertFalse(state.can("app", "delete"))
            assertFalse(state.can("group"))
        }

    @Test
    fun `a failed read allows nothing and is reported apart from a denial`() =
        runTest {
            val server = FakeServer().apply { answer = { error("offline") } }
            val state = newState(server)
            state.setUser(alice)
            state.load()
            advanceUntilIdle()

            assertNotNull(state.error)
            assertNull(state.capabilities)
            assertFalse(state.isLoading)
            for (resource in listOf("user", "app", "group", "")) {
                for (action in listOf("view", "create", "update", "delete")) {
                    assertFalse("$resource $action", state.can(resource, action))
                }
            }
            val access = state.recordAccess("user", isReadOnly = false, isOwnedByOU = true)
            assertEquals(RecordAccess(readOnly = true, canDelete = false, canChangeState = false), access)
        }

    @Test
    fun `retry reads again after a failure`() =
        runTest {
            val server = FakeServer().apply { answer = { error("offline") } }
            val state = newState(server)
            state.setUser(alice)
            state.load()
            advanceUntilIdle()

            server.answer = { ALL_ALLOWED }
            state.retry()
            advanceUntilIdle()

            assertEquals(2, server.reads)
            assertNull(state.error)
            assertTrue(state.can("user"))
        }

    @Test
    fun `several consumers share one read`() =
        runTest {
            val server = FakeServer().apply { gate = CompletableDeferred() }
            val state = newState(server)
            state.setUser(alice)
            repeat(5) { state.load() }
            state.retry() // ignored while a read runs
            server.gate!!.complete(Unit)
            advanceUntilIdle()
            repeat(5) { state.load() }
            advanceUntilIdle()

            assertEquals(1, server.reads)
        }

    @Test
    fun `reads again when the user, their organization unit or their permissions change`() =
        runTest {
            val server = FakeServer()
            val state = newState(server)
            state.setUser(alice)
            state.load()
            advanceUntilIdle()
            assertEquals(1, server.reads)

            // The same user, organization unit and permissions keep the answer.
            state.setUser(User(alice.claims + ("name" to "Alice")))
            advanceUntilIdle()
            assertEquals(1, server.reads)

            state.setUser(User(mapOf("sub" to "bob", "ouId" to "ou-1")))
            advanceUntilIdle()
            assertEquals(2, server.reads)

            state.setUser(User(mapOf("sub" to "bob", "ouId" to "ou-2")))
            advanceUntilIdle()
            assertEquals(3, server.reads)

            server.permissions = listOf("system:user:view")
            state.setUser(User(mapOf("sub" to "bob", "ouId" to "ou-2")))
            advanceUntilIdle()
            assertEquals(4, server.reads)

            // Permission order does not matter.
            server.permissions = listOf("b", "a")
            state.setUser(User(mapOf("sub" to "bob", "ouId" to "ou-2")))
            advanceUntilIdle()
            server.permissions = listOf("a", "b")
            state.setUser(User(mapOf("sub" to "bob", "ouId" to "ou-2")))
            advanceUntilIdle()
            assertEquals(5, server.reads)
        }

    @Test
    fun `sign-out clears the answer and the next use reads again`() =
        runTest {
            val server = FakeServer()
            val state = newState(server)
            state.setUser(alice)
            state.load()
            advanceUntilIdle()

            state.setUser(null)
            assertNull(state.capabilities)
            assertFalse(state.can("user"))

            state.setUser(alice)
            advanceUntilIdle()
            assertEquals(2, server.reads)
            assertTrue(state.can("user"))
        }

    @Test
    fun `a superseded read is discarded`() =
        runTest {
            val first = CompletableDeferred<Capabilities>()
            val second = Capabilities(CapabilityScope("ou", "ou-2"), mapOf("group" to mapOf("view" to true)))
            var calls = 0
            val state =
                CapabilitiesState(
                    this,
                    read = { if (++calls == 1) first.await() else second },
                    grantedPermissions = { emptyList() },
                )
            state.setUser(alice)
            state.load()
            advanceUntilIdle()

            state.setUser(User(mapOf("sub" to "alice", "ouId" to "ou-2")))
            first.complete(ALL_ALLOWED)
            advanceUntilIdle()

            assertSame(second, state.capabilities)
            assertFalse(state.can("user"))

            // A read still in flight at sign-out never lands either.
            val late = CompletableDeferred<Capabilities>()
            val signedOut = CapabilitiesState(this, read = { late.await() }, grantedPermissions = { emptyList() })
            signedOut.setUser(alice)
            signedOut.load()
            advanceUntilIdle()
            signedOut.setUser(null)
            late.complete(ALL_ALLOWED)
            advanceUntilIdle()
            assertNull(signedOut.capabilities)
        }

    @Test
    fun `the root state drives the capabilities from its user`() =
        runTest {
            val client = mockk<ThunderIDClient>()
            coEvery { client.getCapabilities(any()) } returns ALL_ALLOWED
            coEvery { client.getGrantedPermissions() } returns listOf("system")
            coEvery { client.isSignedIn() } returns true andThen false
            coEvery { client.getUser() } returns alice
            val root = ThunderIDState(client, ThunderIDI18n(), this)
            root.isInitialized = true
            root.fetchUserProfileEnabled = false

            root.refresh()
            root.capabilities.load()
            advanceUntilIdle()
            assertTrue(root.capabilities.can("user"))

            root.refresh() // signed out
            assertFalse(root.capabilities.can("user"))
            coVerify(exactly = 1) { client.getCapabilities(any()) }
        }

    @Test
    fun `the guard checks loading first, then the error, then the actions`() =
        runTest {
            val server = FakeServer().apply { gate = CompletableDeferred() }
            val state = newState(server)
            val anyOf = listOf("group" to "view", "user" to "create")
            state.setUser(alice)
            assertEquals(CapabilityGuardOutcome.DENIED, capabilityGuardOutcome(state, anyOf))

            state.load()
            assertEquals(CapabilityGuardOutcome.LOADING, capabilityGuardOutcome(state, anyOf))

            server.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(CapabilityGuardOutcome.ALLOWED, capabilityGuardOutcome(state, anyOf))
            assertEquals(CapabilityGuardOutcome.DENIED, capabilityGuardOutcome(state, listOf("app" to "create")))

            server.gate = null
            server.answer = { error("offline") }
            state.retry()
            advanceUntilIdle()
            assertEquals(CapabilityGuardOutcome.ERROR, capabilityGuardOutcome(state, anyOf))
        }

    @Test
    fun `sharing policies are gated on the resource they share`() {
        assertEquals(
            listOf("user", "app", "role", "flow"),
            SharingResourceType.values().map { it.capabilityResource },
        )
    }
}
