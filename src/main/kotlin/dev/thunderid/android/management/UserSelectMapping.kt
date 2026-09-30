// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/** The user-directory loader built into `UserSelectField`. */
internal typealias FetchUsers = FetchPagedOptions<ManagedUser>

/**
 * Chooses which attribute of a [ManagedUser] is the label and which is the value, for a picker
 * that does not want the default `display -> username -> email -> id` chain.
 */
typealias UserSelectOptionMapping = PagedSelectOptionMapping<ManagedUser>

private fun attributeText(
    user: ManagedUser,
    key: String,
): String? {
    val raw = user.attributes?.get(key) as? String ?: return null
    val trimmed = raw.trim()
    return trimmed.ifEmpty { null }
}

/**
 * Converts one directory user into an option: submits the user's ID and labels it from
 * `display`, then `username`, then `email`, unless [mapping] says otherwise. `display` equal to
 * the ID is treated as unset, since the backend returns the ID as display when a user type has
 * no display attribute configured.
 */
fun toUserSelectOption(
    user: ManagedUser,
    mapping: UserSelectOptionMapping? = null,
): PagedSelectOption? {
    if (mapping != null) return toPagedSelectOption(user, mapping)

    val display = user.display?.trim()
    val label =
        display.takeIf { !it.isNullOrEmpty() && it != user.id }
            ?: attributeText(user, "username")
            ?: attributeText(user, "email")
            ?: user.id
    return PagedSelectOption(label = label, value = user.id)
}

/** Converts a page of users into picker options, dropping any user the mapping has no value for. */
fun toUserSelectOptions(
    users: List<ManagedUser>,
    mapping: UserSelectOptionMapping? = null,
): List<PagedSelectOption> = users.mapNotNull { toUserSelectOption(it, mapping) }

/**
 * Converts a `client.users.list` response into a loader page. The next offset comes from the
 * backend's own `count`, so users dropped by the mapping never shift the paging.
 */
fun toUserSelectPage(
    response: ManagedUserListResponse,
    requestOffset: Int,
    mapping: UserSelectOptionMapping? = null,
): PagedSelectPage<ManagedUser> =
    PagedSelectPage(
        options = toUserSelectOptions(response.users, mapping),
        nextOffset = PagedSelectPaging.computeNextPageOffset(requestOffset, response.count, response.totalResults),
        totalResults = response.totalResults,
    )
