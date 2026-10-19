// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import dev.thunderid.android.management.SelectOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectListLayoutTest {
    @Test
    fun `the first load shows loading and no load more row`() {
        val layout = SelectListLayout.of(optionCount = 0, loading = true, canLoadMore = true)

        assertEquals(SelectListStatus.LOADING, layout.status)
        assertFalse(layout.showsLoadMore)
    }

    @Test
    fun `loading more keeps the load more row so it can show progress`() {
        val layout = SelectListLayout.of(optionCount = 30, loading = true, canLoadMore = true)

        assertEquals(SelectListStatus.NONE, layout.status)
        assertTrue(layout.showsLoadMore)
    }

    @Test
    fun `options with more to load show the load more row`() {
        val layout = SelectListLayout.of(optionCount = 30, loading = false, canLoadMore = true)

        assertEquals(SelectListStatus.NONE, layout.status)
        assertTrue(layout.showsLoadMore)
    }

    @Test
    fun `a failed first load leaves only the load more row to try again`() {
        val layout = SelectListLayout.of(optionCount = 0, loading = false, canLoadMore = true)

        assertEquals(SelectListStatus.NONE, layout.status)
        assertTrue(layout.showsLoadMore)
    }

    @Test
    fun `no options and nothing more to load shows the empty text`() {
        val layout = SelectListLayout.of(optionCount = 0, loading = false, canLoadMore = false)

        assertEquals(SelectListStatus.EMPTY, layout.status)
        assertFalse(layout.showsLoadMore)
    }

    @Test
    fun `the last page has no load more row`() {
        val layout = SelectListLayout.of(optionCount = 75, loading = false, canLoadMore = false)

        assertEquals(SelectListStatus.NONE, layout.status)
        assertFalse(layout.showsLoadMore)
    }

    @Test
    fun `an empty list that cannot load more and is still loading shows loading`() {
        val layout = SelectListLayout.of(optionCount = 0, loading = true, canLoadMore = false)

        assertEquals(SelectListStatus.LOADING, layout.status)
    }
}

class ResolveSelectOptionTest {
    private val loaded = listOf(SelectOption("Ada", "u-1"))
    private val picked = SelectOption("Grace", "u-2")
    private val saved = SelectOption("Linus", "u-3")

    @Test
    fun `a loaded option labels its value`() {
        assertEquals("Ada", resolveSelectOption("u-1", loaded, picked, saved)?.label)
    }

    @Test
    fun `the option last picked labels a value that is not loaded`() {
        assertEquals("Grace", resolveSelectOption("u-2", loaded, picked, saved)?.label)
    }

    @Test
    fun `the selected option labels a value that is neither loaded nor picked`() {
        assertEquals("Linus", resolveSelectOption("u-3", loaded, picked, saved)?.label)
    }

    @Test
    fun `an option only labels its own value`() {
        assertNull(resolveSelectOption("u-9", loaded, picked, saved))
    }

    @Test
    fun `an empty value has no option`() {
        assertNull(resolveSelectOption("", loaded, picked, saved))
    }
}
