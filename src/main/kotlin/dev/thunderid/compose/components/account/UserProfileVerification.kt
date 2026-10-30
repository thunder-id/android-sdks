// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.AttributeVerification
import dev.thunderid.android.R
import dev.thunderid.android.UserProfile
import dev.thunderid.android.VerifiedAttribute
import dev.thunderid.compose.i18n.ThunderIDI18n
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Verification status of a profile attribute, as `/users/me` reports it. */
enum class AttributeVerificationStatus {
    VERIFIED,
    UNVERIFIED,
}

/**
 * The status to show for [field], or `null` for none: when the server does not report verification
 * ([verifiedAttributes] is `null`), the field is not a top-level single-valued `email` or `mobile`
 * property, it has no stored value, or it is being edited.
 */
internal fun verificationStatusOf(
    field: ProfileField,
    verifiedAttributes: Map<String, VerifiedAttribute>?,
    isEditing: Boolean,
): AttributeVerificationStatus? {
    if (verifiedAttributes == null || isEditing) return null
    if (!AttributeVerification.isVerifiableType(field.schema.type)) return null
    val value = field.rawValue as? String
    if (value.isNullOrBlank()) return null
    return if (field.name in verifiedAttributes) AttributeVerificationStatus.VERIFIED else AttributeVerificationStatus.UNVERIFIED
}

/** Whether to offer the verify action for [field]: a stored, unverified `email` or `mobile` value, not being edited. */
internal fun canVerifyField(
    field: ProfileField,
    verifiedAttributes: Map<String, VerifiedAttribute>?,
    isEditing: Boolean,
    verifyingField: String?,
): Boolean =
    !field.isReadonly &&
        verifyingField != field.name &&
        verificationStatusOf(field, verifiedAttributes, isEditing) == AttributeVerificationStatus.UNVERIFIED &&
        AttributeVerification.isVerifiableAttribute(field.name, mapOf(field.name to field.schema))

/**
 * Whether saving [field] should open its verification: only a changed `email` value, stored and
 * reported unverified by [updated]. An absent `verifiedAttributes` leaves the status unknown, so
 * nothing opens.
 */
internal fun shouldVerifyAfterSave(
    field: ProfileField,
    updated: UserProfile,
): Boolean {
    if (!AttributeVerification.opensAfterSave(field.schema.type)) return false
    if (!AttributeVerification.isVerifiableAttribute(field.name, mapOf(field.name to field.schema))) return false
    val stored = updated.attributes[field.name] as? String
    if (stored.isNullOrBlank() || stored == field.rawValue) return false
    val verified = updated.verifiedAttributes ?: return false
    return field.name !in verified
}

/** Formats a `verifiedAt` timestamp for [locale], or returns it as it came when it does not parse. */
internal fun formatVerifiedAt(
    verifiedAt: String,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    runCatching {
        OffsetDateTime
            .parse(verifiedAt)
            .atZoneSameInstant(zone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale))
    }.getOrDefault(verifiedAt)

/**
 * What assistive technology reads for a status chip: "Verified on <date>" when the server gave the
 * time, otherwise the status itself. Android has no hover title, so the time lives here.
 */
internal fun verificationStatusDescription(
    status: AttributeVerificationStatus,
    verifiedAt: String?,
    i18n: ThunderIDI18n,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val verified = status == AttributeVerificationStatus.VERIFIED
    if (!verified || verifiedAt.isNullOrBlank()) {
        return i18n.resolve(if (verified) "userProfile.verification.verified" else "userProfile.verification.unverified")
    }
    return i18n
        .resolve("userProfile.verification.verifiedAt")
        .replace("{date}", formatVerifiedAt(verifiedAt, Locale.forLanguageTag(i18n.activeLocale), zone))
}

/** The Verified or Unverified chip the styled [UserProfile] shows beside an eligible attribute. */
@Composable
internal fun VerificationStatusChip(
    field: ProfileField,
    status: AttributeVerificationStatus,
    verifiedAt: String?,
    i18n: ThunderIDI18n,
) {
    val verified = status == AttributeVerificationStatus.VERIFIED
    val label = i18n.resolve(if (verified) "userProfile.verification.verified" else "userProfile.verification.unverified")
    val description = verificationStatusDescription(status, verifiedAt, i18n)
    val color = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            Modifier
                .testTag("thunderid-verification-status-${field.name}")
                .semantics { contentDescription = description },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Full-screen verification panel the styled [UserProfile] opens for [field], matching its field-edit
 * dialog. Keyed by the attribute, so each opening starts one verification.
 */
@Composable
internal fun VerifyAttributeDialog(
    field: ProfileField,
    state: UserProfileState,
    flowHandle: String,
    i18n: ThunderIDI18n,
) {
    val label = field.schema.displayName ?: field.schema.description ?: field.name
    Dialog(
        onDismissRequest = { state.closeVerification() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { state.closeVerification() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = i18n.resolve("verifyAttribute.cancel"),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        i18n.resolve("userProfile.section"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                        // BaseUserProfile takes the re-read profile through LocalVerifiedProfileSink.
                        VerifyAttribute(
                            attribute = field.name,
                            flowHandle = flowHandle,
                            attributeDisplayName = label,
                            onDone = { state.closeVerification() },
                            onCancel = { state.closeVerification() },
                        )
                    }
                }
            }
        }
    }
}
