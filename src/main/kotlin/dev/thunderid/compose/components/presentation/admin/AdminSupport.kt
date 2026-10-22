// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n

/** An option of an admin select field: the [label] shown and the [value] submitted. */
data class AdminOption(
    val label: String,
    val value: String,
)

/** An identity that can be added to a group or assigned a role, as offered by the identity picker. */
data class IdentityCandidate(
    val id: String,
    val label: String,
)

/**
 * Turns a management failure into a translated message for its kind: a missing permission, a missing
 * resource, or [fallbackKey] for anything else. The server's own error text is never shown. Returns `null`
 * when there is no [error].
 */
fun toManagementErrorMessage(
    i18n: ThunderIDI18n,
    error: Throwable?,
    fallbackKey: String,
): String? {
    if (error == null) return null
    return when ((error as? IAMException)?.code) {
        ThunderIDErrorCode.FORBIDDEN -> i18n.resolve("admin.common.errors.forbidden")
        ThunderIDErrorCode.NOT_FOUND -> i18n.resolve("admin.common.errors.not_found")
        else -> i18n.resolve(fallbackKey)
    }
}

/** Resolves [key] and substitutes each `{name}` placeholder from [params]. */
internal fun ThunderIDI18n.translate(
    key: String,
    vararg params: Pair<String, String>,
): String = formatTemplate(resolve(key), params.toMap())

internal fun formatTemplate(
    template: String,
    params: Map<String, String>,
): String = params.entries.fold(template) { text, (name, value) -> text.replace("{$name}", value) }

/** The 1-based first and last row numbers of a page, for the `{start} to {end} of {total}` label. */
internal data class PageRange(
    val start: Int,
    val end: Int,
    val total: Int,
)

internal fun pageRange(
    offset: Int,
    rowCount: Int,
    total: Int,
): PageRange =
    if (rowCount == 0) {
        PageRange(0, 0, total)
    } else {
        PageRange(offset + 1, offset + rowCount, maxOf(total, offset + rowCount))
    }

/** The candidates of one kind that are not already present. */
internal fun availableCandidates(
    candidates: List<IdentityCandidate>?,
    excludedIds: Collection<String>,
): List<IdentityCandidate> = candidates.orEmpty().filter { it.id !in excludedIds }

/** The attributes of a managed resource worth showing, without the [hidden] ones. */
internal fun visibleAttributes(
    attributes: Map<String, Any?>?,
    hidden: Collection<String>,
): List<Pair<String, Any?>> = attributes.orEmpty().filterKeys { it !in hidden }.toList()

/** The attributes that can be edited in place: the visible ones holding text. */
internal fun editableAttributes(visible: List<Pair<String, Any?>>): Map<String, String> =
    visible.mapNotNull { (name, value) -> (value as? String)?.let { name to it } }.toMap()

internal fun stringifyAttribute(value: Any?): String =
    when (value) {
        null -> {
            ""
        }

        is String -> {
            value
        }

        is Number, is Boolean -> {
            value.toString()
        }

        else -> {
            com.google.gson
                .Gson()
                .toJson(value)
        }
    }

/**
 * The organization unit a create form places its resource in: [ouId] when given, otherwise the signed-in
 * user's own, read from the `ouId` claim and, failing that, from the user's profile.
 */
@Composable
internal fun rememberDefaultOuId(ouId: String?): String? {
    val state = LocalThunderID.current
    val fromClaims = state.user?.get("ouId") as? String
    var fromProfile by remember { mutableStateOf<String?>(null) }
    val needsProfile = ouId == null && fromClaims == null && state.user != null
    LaunchedEffect(needsProfile) {
        if (needsProfile) fromProfile = runCatching { state.client.getUserProfile().ouId }.getOrNull()
    }
    return ouId ?: fromClaims ?: fromProfile
}
