package dev.thunderid.compose

import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.*
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
        val i18n = ThunderIDI18n(
            bundles = mapOf("fr-FR" to mapOf("signIn.button" to "Se connecter")),
            language = "en-US",
        )
        i18n.setLocale("fr-FR")
        assertEquals("fr-FR", i18n.activeLocale)
        assertEquals("Se connecter", i18n.resolve("signIn.button"))
    }

    @Test
    fun `i18n falls back through fallback locale`() {
        val i18n = ThunderIDI18n(
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
        val required = listOf(
            "signIn.button", "signOut.button", "signUp.button",
            "userProfile.title", "userProfile.save",
            "organizationList.empty", "createOrganization.submit",
            "languageSwitcher.title", "acceptInvite.submit", "inviteUser.submit",
        )
        required.forEach { key ->
            assertNotNull("Missing default string for key: $key", DefaultStrings.all[key])
        }
    }
}
