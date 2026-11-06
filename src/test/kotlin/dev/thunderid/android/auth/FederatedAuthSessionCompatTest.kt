// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.auth

import android.content.Context
import android.net.Uri
import dev.thunderid.android.api.auth.FederatedAuthSession as ApiFederatedAuthSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FederatedAuthSessionCompatTest {
    @After
    fun tearDown() {
        unmockkObject(ApiFederatedAuthSession)
    }

    @Test
    fun `launch delegates to api auth session`() = runBlocking {
        mockkObject(ApiFederatedAuthSession)
        val context = mockk<Context>()
        val redirectUrl = "thunderid://callback?code=123"
        val redirect = Uri.parse(redirectUrl)
        coEvery { ApiFederatedAuthSession.launch(context, redirectUrl) } returns redirect

        val result = FederatedAuthSession.launch(context, redirectUrl)

        assertEquals(redirect, result)
        coVerify(exactly = 1) { ApiFederatedAuthSession.launch(context, redirectUrl) }
    }

    @Test
    fun `onRedirect delegates to api auth session`() {
        mockkObject(ApiFederatedAuthSession)
        val redirect = Uri.parse("thunderid://callback?code=123")
        every { ApiFederatedAuthSession.onRedirect(redirect) } returns Unit

        FederatedAuthSession.onRedirect(redirect)

        verify(exactly = 1) { ApiFederatedAuthSession.onRedirect(redirect) }
    }

    @Test
    fun `cancelIfPending delegates to api auth session`() {
        mockkObject(ApiFederatedAuthSession)
        every { ApiFederatedAuthSession.cancelIfPending() } returns Unit

        FederatedAuthSession.cancelIfPending()

        verify(exactly = 1) { ApiFederatedAuthSession.cancelIfPending() }
    }
}
