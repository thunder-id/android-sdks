// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInTranslationsTest {
    private val placeholder = Regex("\\{[A-Za-z]+\\}")
    private val credential = setOf("{credential}", "{credentialLower}")

    @Test
    fun `ships the locales the JavaScript SDK ships`() {
        assertEquals(
            listOf("en-US", "fr-FR", "hi-IN", "ja-JP", "pt-BR", "pt-PT", "si-LK", "ta-IN", "te-IN"),
            ThunderIDI18n.BUILT_IN_LOCALES,
        )
    }

    @Test
    fun `every locale translates every key, and only the placeholders the English string has`() {
        ThunderIDI18n.BUILT_IN_LOCALES.forEach { locale ->
            val strings = ThunderIDI18n.builtInStrings(locale)
            assertNotNull(locale, strings)
            assertEquals(locale, DefaultStrings.all.keys, strings!!.keys)
            strings.forEach { (key, value) ->
                val english = placeholder.findAll(DefaultStrings.all.getValue(key)).map { it.value }.toSet()
                val allowed = if (english.any { it in credential }) english + credential else english
                val used = placeholder.findAll(value).map { it.value }.toSet()
                assertTrue("$locale $key: $value", (used - allowed).isEmpty())
                assertTrue("$locale $key is empty", value.isNotBlank())
            }
        }
    }

    @Test
    fun `resolves the active locale's strings`() {
        assertEquals("Autorisations", ThunderIDI18n(language = "fr-FR").resolve("admin.roles.permissions.title"))
        assertEquals("権限を追加", ThunderIDI18n(language = "ja-JP").resolve("admin.roles.permissions.add"))
        assertEquals("Guardar", ThunderIDI18n(language = "pt-PT").resolve("userProfile.save"))
        assertEquals("Salvar", ThunderIDI18n(language = "pt-BR").resolve("userProfile.save"))
        assertEquals("Permissions", ThunderIDI18n().resolve("admin.roles.permissions.title"))
    }

    @Test
    fun `matches a locale by its language alone when it names no built-in locale`() {
        assertEquals("ログイン", ThunderIDI18n(language = "ja").resolve("signIn.button"))
        assertEquals("ログイン", ThunderIDI18n(language = "ja_JP").resolve("signIn.button"))
        assertEquals("Sign in", ThunderIDI18n(language = "en-GB").resolve("signIn.button"))
        assertEquals("Salvar", ThunderIDI18n(language = "pt").resolve("userProfile.save"))
        assertNull(ThunderIDI18n.builtInStrings("de-DE"))
        assertEquals("Sign in", ThunderIDI18n(language = "de-DE").resolve("signIn.button"))
    }

    @Test
    fun `an application bundle wins over the built-in strings`() {
        val i18n =
            ThunderIDI18n(
                bundles = mapOf("fr-FR" to mapOf("signIn.button" to "Entrer"), "en-US" to mapOf("signOut.button" to "Leave")),
                language = "fr-FR",
            )

        assertEquals("Entrer", i18n.resolve("signIn.button"))
        assertEquals("Se déconnecter", i18n.resolve("signOut.button"))
        assertEquals("unknown.key", i18n.resolve("unknown.key"))
    }
}
