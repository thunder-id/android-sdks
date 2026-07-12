/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.compose

import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ComponentTests {
    // ── ThunderIDI18n ──────────────────────────────────────────────────────────

    @Test
    fun `i18n resolves default string`() {
        val i18n = ThunderIDI18n()
        assertEquals("Sign in", i18n.resolve("signIn.button"))
    }

    @Test
    fun `i18n resolves custom bundle`() {
        val i18n = ThunderIDI18n(bundles = mapOf("en-US" to mapOf("signIn.button" to "Log in")))
        assertEquals("Log in", i18n.resolve("signIn.button"))
    }

    @Test
    fun `i18n falls back to default for missing key`() {
        val i18n = ThunderIDI18n(bundles = mapOf("en-US" to emptyMap()))
        assertEquals("Sign out", i18n.resolve("signOut.button"))
    }

    @Test
    fun `i18n sets locale`() {
        val i18n =
            ThunderIDI18n(
                bundles = mapOf("fr-FR" to mapOf("signIn.button" to "Se connecter")),
                language = "en-US",
            )
        i18n.setLocale("fr-FR")
        assertEquals("fr-FR", i18n.activeLocale)
        assertEquals("Se connecter", i18n.resolve("signIn.button"))
    }

    @Test
    fun `i18n falls back through fallback locale`() {
        val i18n =
            ThunderIDI18n(
                bundles = mapOf("es-ES" to mapOf("signIn.button" to "Iniciar sesión")),
                language = "de-DE",
                fallbackLanguage = "es-ES",
            )
        assertEquals("Iniciar sesión", i18n.resolve("signIn.button"))
    }

    @Test
    fun `i18n returns key for unknown string`() {
        val i18n = ThunderIDI18n()
        assertEquals("not.a.real.key", i18n.resolve("not.a.real.key"))
    }

    // ── DefaultStrings ───────────────────────────────────────────────────────

    @Test
    fun `DefaultStrings contains all expected keys`() {
        val required =
            listOf(
                "signIn.button",
                "signOut.button",
                "signUp.button",
                "userProfile.title",
                "userProfile.save",
                "organizationList.empty",
                "createOrganization.submit",
                "languageSwitcher.title",
                "acceptInvite.submit",
                "inviteUser.submit",
            )
        required.forEach { key ->
            assertNotNull("Missing default string for key: $key", DefaultStrings.all[key])
        }
    }
}
