// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedSelectPagingTest {
    @Test
    fun `computeNextPageOffset advances by count`() {
        assertEquals(30, PagedSelectPaging.computeNextPageOffset(0, count = 30, totalResults = 75))
        assertEquals(60, PagedSelectPaging.computeNextPageOffset(30, count = 30, totalResults = 75))
    }

    @Test
    fun `computeNextPageOffset stops at total`() {
        assertNull(PagedSelectPaging.computeNextPageOffset(60, count = 15, totalResults = 75))
    }

    @Test
    fun `computeNextPageOffset stops on an empty page`() {
        assertNull(PagedSelectPaging.computeNextPageOffset(0, count = 0, totalResults = 0))
    }

    @Test
    fun `computeNextPageOffset still advances without a totalResults`() {
        assertEquals(30, PagedSelectPaging.computeNextPageOffset(0, count = 30, totalResults = null))
    }

    @Test
    fun `isAdvancingPageOffset accepts a greater offset`() {
        assertTrue(PagedSelectPaging.isAdvancingPageOffset(0, 30))
    }

    @Test
    fun `isAdvancingPageOffset rejects null`() {
        assertEquals(false, PagedSelectPaging.isAdvancingPageOffset(0, null))
    }

    @Test
    fun `isAdvancingPageOffset rejects a non-advancing value`() {
        assertEquals(false, PagedSelectPaging.isAdvancingPageOffset(30, 30))
        assertEquals(false, PagedSelectPaging.isAdvancingPageOffset(30, 10))
    }

    @Test
    fun `dedupe keeps first position and newest data`() {
        val existing = listOf(PagedSelectOption(label = "Old", value = "1"))
        val incoming =
            listOf(
                PagedSelectOption(label = "New", value = "1"),
                PagedSelectOption(label = "Two", value = "2"),
            )

        val result = PagedSelectPaging.dedupePagedSelectOptions(existing, incoming)

        assertEquals(listOf("1", "2"), result.map { it.value })
        assertEquals("New", result.first().label)
    }
}

class PagedSelectMappingTest {
    private data class Product(
        val sku: String,
        val title: String?,
    )

    private val mapping = PagedSelectOptionMapping<Product>(label = { it.title }, value = { it.sku })

    @Test
    fun `uses the mapped label and value`() {
        val option = toPagedSelectOption(Product("p-1", "Widget"), mapping)

        assertEquals(PagedSelectOption(label = "Widget", value = "p-1"), option)
    }

    @Test
    fun `falls back to the value when there is no label`() {
        assertEquals("p-1", toPagedSelectOption(Product("p-1", null), mapping)?.label)
    }

    @Test
    fun `drops an item without a value`() {
        val noValue = PagedSelectOptionMapping<Product>(label = { it.title }, value = { null })

        assertNull(toPagedSelectOption(Product("p-1", "Widget"), noValue))
    }

    @Test
    fun `converts a page and skips items without a value`() {
        val selective = PagedSelectOptionMapping<Product>(label = { it.title }, value = { it.sku.ifEmpty { null } })
        val items = listOf(Product("p-1", "A"), Product("", "B"), Product("p-3", "C"))

        assertEquals(listOf("p-1", "p-3"), toPagedSelectOptions(items, selective).map { it.value })
    }
}

class UserSelectMappingTest {
    private fun user(
        id: String = "u-1",
        display: String? = null,
        username: String? = null,
        email: String? = null,
    ): ManagedUser {
        val attributes =
            buildMap<String, Any?> {
                username?.let { put("username", it) }
                email?.let { put("email", it) }
            }
        return ManagedUser(id = id, ouId = "ou", type = "person", attributes = attributes.ifEmpty { null }, display = display)
    }

    @Test
    fun `prefers display`() {
        val option = toUserSelectOption(user(display = "Ada Lovelace", username = "ada"))
        assertEquals(PagedSelectOption(label = "Ada Lovelace", value = "u-1"), option)
    }

    @Test
    fun `falls back to username when display equals id`() {
        // The backend returns the ID as display when the user type has no display attribute.
        val option = toUserSelectOption(user(id = "u-1", display = "u-1", username = "ada"))
        assertEquals("ada", option?.label)
    }

    @Test
    fun `falls back to email when there is no username`() {
        val option = toUserSelectOption(user(email = "ada@example.com"))
        assertEquals("ada@example.com", option?.label)
    }

    @Test
    fun `falls back to id when nothing else is set`() {
        val option = toUserSelectOption(user())
        assertEquals("u-1", option?.label)
    }

    @Test
    fun `always submits the id`() {
        val option = toUserSelectOption(user(id = "u-42", display = "Grace Hopper"))
        assertEquals("u-42", option?.value)
    }

    @Test
    fun `mapping overrides label and value`() {
        val mapping =
            UserSelectOptionMapping(
                label = { it.attributes?.get("email") as? String },
                value = { it.attributes?.get("email") as? String },
            )

        val option = toUserSelectOption(user(display = "Ada Lovelace", email = "ada@example.com"), mapping)

        assertEquals(PagedSelectOption(label = "ada@example.com", value = "ada@example.com"), option)
    }

    @Test
    fun `mapping drops a user it has no value for`() {
        val mapping = UserSelectOptionMapping(label = { null }, value = { it.attributes?.get("email") as? String })

        assertNull(toUserSelectOption(user(), mapping))
    }

    @Test
    fun `toUserSelectPage computes the next offset from count`() {
        val response =
            ManagedUserListResponse(
                totalResults = 75,
                startIndex = 1,
                count = 30,
                users = listOf(user(id = "u-1", display = "Ada")),
            )

        val page = toUserSelectPage(response, requestOffset = 0)

        assertEquals(30, page.nextOffset)
        assertEquals("Ada", page.options?.first()?.label)
    }

    @Test
    fun `toUserSelectPage stops at the last page`() {
        val response = ManagedUserListResponse(totalResults = 75, startIndex = 61, count = 15, users = emptyList())

        val page = toUserSelectPage(response, requestOffset = 60)

        assertNull(page.nextOffset)
    }
}

class ManagedSelectMappingTest {
    private fun flow(
        id: String,
        name: String,
        handle: String = "login",
    ) = ManagedFlow(id = id, handle = handle, name = name, flowType = "AUTHENTICATION")

    private fun userType(
        id: String,
        handle: String,
        displayName: String? = "Staff",
    ) = ManagedUserType(id = id, handle = handle, displayName = displayName)

    private fun flows(
        items: List<ManagedFlow>,
        startIndex: Int,
        totalResults: Int,
    ) = ManagedFlowListResponse(totalResults = totalResults, startIndex = startIndex, count = items.size, flows = items)

    private fun types(
        items: List<ManagedUserType>,
        startIndex: Int,
        totalResults: Int,
    ) = ManagedUserTypeListResponse(totalResults = totalResults, startIndex = startIndex, count = items.size, types = items)

    @Test
    fun `a flow is shown by name and submits its id`() {
        val option = toAuthFlowSelectOption(flow(id = "flow-1", name = "Password login"))

        assertEquals(PagedSelectOption(label = "Password login", value = "flow-1"), option)
    }

    @Test
    fun `a flow with an empty name falls back to its handle then its id`() {
        assertEquals("sso", toAuthFlowSelectOption(flow(id = "flow-1", name = "", handle = "sso")).label)
        assertEquals("flow-2", toAuthFlowSelectOption(flow(id = "flow-2", name = " ", handle = "")).label)
    }

    @Test
    fun `a user type is shown and submitted by its handle never its display name`() {
        val option = toUserTypeSelectOption(userType(id = "type-1", handle = "employee"))

        assertEquals(PagedSelectOption(label = "employee", value = "employee"), option)
    }

    @Test
    fun `a user type without a handle is dropped`() {
        assertNull(toUserTypeSelectOption(userType(id = "type-1", handle = "")))
    }

    @Test
    fun `the flow page advances by the backend count and ends on the last page`() {
        val page =
            toAuthFlowSelectPage(
                flows(listOf(flow("f1", "A"), flow("f2", "B")), startIndex = 31, totalResults = 75),
                requestOffset = 30,
            )
        val last = toAuthFlowSelectPage(flows(listOf(flow("f75", "Z")), startIndex = 75, totalResults = 75), requestOffset = 74)

        assertEquals(32, page.nextOffset)
        assertEquals(listOf("f1", "f2"), page.options?.map { it.value })
        assertNull(last.nextOffset)
    }

    @Test
    fun `the user type page still advances when a type is dropped`() {
        val page =
            toUserTypeSelectPage(
                types(listOf(userType("t1", ""), userType("t2", "employee")), startIndex = 1, totalResults = 10),
                requestOffset = 0,
            )

        assertEquals(listOf("employee"), page.options?.map { it.value })
        assertEquals(2, page.nextOffset)
    }

    @Test
    fun `the page size cap matches the backend maximum`() {
        assertEquals(100, PagedSelectLimits.MAX_PAGE_SIZE)
        assertEquals(100, minOf(500, PagedSelectLimits.MAX_PAGE_SIZE))
    }
}

class PagedSelectErrorMappingTest {
    @Test
    fun `trusts an IAMException message`() {
        val failure = mapPagedSelectError(IAMException(ThunderIDErrorCode.SERVER_ERROR, "Upstream exploded"))

        assertEquals("Upstream exploded", failure.message)
    }

    @Test
    fun `does not trust a plain exception`() {
        val failure = mapPagedSelectError(RuntimeException("boom"))

        assertNull(failure.message)
    }
}
