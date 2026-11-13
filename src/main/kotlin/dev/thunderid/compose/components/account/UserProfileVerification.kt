// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.AttributeVerification
import dev.thunderid.android.R
import dev.thunderid.android.VerifiedAttribute
import dev.thunderid.compose.i18n.ThunderIDI18n

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

/**
 * Whether to offer the verify action for [field]: a stored, unverified `email` or `mobile` value, not being
 * edited. A read-only field qualifies too, since verifying does not write the attribute.
 */
internal fun canVerifyField(
    field: ProfileField,
    verifiedAttributes: Map<String, VerifiedAttribute>?,
    isEditing: Boolean,
    verifyingField: String?,
): Boolean =
    verifyingField != field.name &&
        verificationStatusOf(field, verifiedAttributes, isEditing) == AttributeVerificationStatus.UNVERIFIED &&
        AttributeVerification.isVerifiableAttribute(field.name, mapOf(field.name to field.schema))

/** The Verified or Unverified status the styled [UserProfile] shows under an eligible attribute's value and in its editor. */
@Composable
internal fun VerificationStatusLine(
    field: ProfileField,
    status: AttributeVerificationStatus,
    i18n: ThunderIDI18n,
) {
    val verified = status == AttributeVerificationStatus.VERIFIED
    val label = i18n.resolve(if (verified) "userProfile.verification.verified" else "userProfile.verification.unverified")
    VerificationStatusLine(
        verified = verified,
        label = label,
        modifier = Modifier.testTag("thunderid-verification-status-${field.name}"),
    )
}

@Composable
internal fun VerificationStatusLine(
    verified: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.25f
    val color =
        when {
            verified && dark -> Color(0xFF4ADE80)
            verified -> Color(0xFF166534)
            dark -> Color(0xFFF5B544)
            pill -> Color(0xFF92400E)
            else -> Color(0xFFB45309)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            if (pill) {
                modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp)
            } else {
                modifier
            },
    ) {
        Icon(
            painter = painterResource(if (verified) R.drawable.ic_check_circle else R.drawable.ic_alert_circle),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun VerificationActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        contentPadding = PaddingValues(vertical = 13.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
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
                            onComplete = { state.closeVerification() },
                            onDone = { state.closeVerification() },
                            onCancel = { state.closeVerification() },
                        ) { verification ->
                            if (verification.phase != VerifyAttributePhase.COMPLETE) {
                                VerifyAttributePanel(state = verification, onCancel = verification.onCancel)
                            }
                        }
                    }
                }
            }
        }
    }
}
