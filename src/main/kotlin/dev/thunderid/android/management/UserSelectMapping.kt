// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/** The user-directory loader built into `UserSelectField`. */
internal typealias FetchUsers = FetchSelectPage<ManagedUser>

/**
 * Chooses which attribute of a [ManagedUser] is the label and which is the value, for a picker
 * that does not want the default `display -> username -> email -> id` chain.
 */
typealias UserSelectOptionMapping = SelectOptionMapping<ManagedUser>

private fun attributeText(
    user: ManagedUser,
    key: String,
): String? = user.attributes?.get(key) as? String

/** The trimmed text, or `null` when it is empty or only repeats the user's ID. */
private fun labelText(
    text: String?,
    user: ManagedUser,
): String? = text?.trim()?.takeIf { it.isNotEmpty() && it != user.id }

/**
 * Converts one directory user into an option: submits the user's ID and labels it from
 * `display`, then `username`, then `email`, unless [mapping] says otherwise. A label equal to the
 * ID is treated as unset, since the backend returns the ID as display when a user type has no
 * display attribute configured.
 */
fun toUserSelectOption(
    user: ManagedUser,
    mapping: UserSelectOptionMapping? = null,
): SelectOption? {
    if (mapping != null) return toSelectOption(user, mapping)

    val label =
        labelText(user.display, user)
            ?: labelText(attributeText(user, "username"), user)
            ?: labelText(attributeText(user, "email"), user)
            ?: user.id
    return SelectOption(label = label, value = user.id)
}

/** Converts a page of users into picker options, dropping any user the mapping has no value for. */
fun toUserSelectOptions(
    users: List<ManagedUser>,
    mapping: UserSelectOptionMapping? = null,
): List<SelectOption> = users.mapNotNull { toUserSelectOption(it, mapping) }

/**
 * Converts a `client.users.list` response into a loader page. The next offset comes from the
 * backend's own `count`, so users dropped by the mapping never shift the paging.
 */
fun toUserSelectPage(
    response: ManagedUserListResponse,
    requestOffset: Int,
    mapping: UserSelectOptionMapping? = null,
): SelectPage<ManagedUser> =
    toListSelectPage(
        options = toUserSelectOptions(response.users, mapping),
        count = response.count,
        totalResults = response.totalResults,
        requestOffset = requestOffset,
    )
