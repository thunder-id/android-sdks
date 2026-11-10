// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.JsonParser
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class CredentialTransport : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return ThunderIDHttpResponse(204, "")
    }
}

class UpdateUserCredentialsTest {
    private val transport = CredentialTransport()

    private suspend fun client(): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "user-token", tokenType = "Bearer", expiresIn = 3600))
        val config = ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "client")
        return ThunderIDClient(transport).apply { initialize(config, storage) }
    }

    private fun sentBody(): Map<String, String> =
        JsonParser
            .parseString(transport.requests.single().body)
            .asJsonObject
            .entrySet()
            .associate { it.key to it.value.asString }

    @Test
    fun `sends the name and both values`() =
        runTest {
            client().updateUserCredentials(attribute = "password", currentValue = "0ldP@ss", newValue = "n3wP@ss")

            assertEquals(mapOf("name" to "password", "currentValue" to "0ldP@ss", "newValue" to "n3wP@ss"), sentBody())
        }

    @Test
    fun `leaves out a blank current value`() =
        runTest {
            client().updateUserCredentials(attribute = "pin", currentValue = "", newValue = "1234")

            assertEquals(mapOf("name" to "pin", "newValue" to "1234"), sentBody())
        }
}
