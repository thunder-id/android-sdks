// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/** A registered gateway. No read returns its management key. */
data class Gateway(
    val id: String,
    val name: String? = null,
    val baseUrl: String? = null,
    val caCertificate: String? = null,
    val isDefault: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * The payload used to register a gateway.
 *
 * @property key The management token to present to the gateway. Leave it out and the server generates one.
 *   Write-only, and left out of [toString].
 * @property isDefault Makes this the default gateway.
 */
data class RegisterGatewayRequest(
    val name: String,
    val baseUrl: String,
    val key: String? = null,
    val caCertificate: String? = null,
    val isDefault: Boolean? = null,
) {
    override fun toString(): String =
        "RegisterGatewayRequest(name=$name, baseUrl=$baseUrl, key=${key?.let { "***" }}, " +
            "caCertificate=$caCertificate, isDefault=$isDefault)"
}

/**
 * The payload used to change a gateway. A field left `null` keeps its value; a new [key] rotates the
 * gateway's credential. [key] is write-only and left out of [toString].
 */
data class UpdateGatewayRequest(
    val name: String? = null,
    val baseUrl: String? = null,
    val caCertificate: String? = null,
    val key: String? = null,
) {
    override fun toString(): String =
        "UpdateGatewayRequest(name=$name, baseUrl=$baseUrl, caCertificate=$caCertificate, " +
            "key=${key?.let { "***" }})"
}

/**
 * A new registration and the management key it issued. This is the only response that carries [key]:
 * configure the gateway with it, since it cannot be read back. [key] is left out of [toString].
 */
data class GatewayRegistration(
    val id: String,
    val key: String,
    val name: String? = null,
    val baseUrl: String? = null,
    val caCertificate: String? = null,
    val isDefault: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    override fun toString(): String = "GatewayRegistration(id=$id, key=***, name=$name, baseUrl=$baseUrl)"
}

/** A user, by id and name, as a configuration version or an apply records it. */
data class ConfigurationActor(
    val id: String,
    val name: String,
)

/** The version a gateway holds, and the one a revert would return it to. */
data class GatewayAppliedVersion(
    val gatewayId: String? = null,
    val appliedVersion: String? = null,
    val previousVersion: String? = null,
    val appliedAt: String? = null,
    val appliedBy: ConfigurationActor? = null,
)

/** Every resource of the version last applied to a gateway. */
data class GatewayAppliedConfiguration(
    val gatewayId: String,
    val resources: List<GatewayAppliedResource>,
    val version: String? = null,
    val appliedAt: String? = null,
    val skipped: List<GatewaySkippedResource>? = null,
)

/**
 * One resource of an applied version: [resource] is what the resource's own read returns, and [parts] the
 * collections served below it, keyed by their path.
 */
data class GatewayAppliedResource(
    val resourceType: String,
    val id: String,
    val resource: Map<String, Any?>,
    val parts: Map<String, Any?>? = null,
)

/** A document of an applied version that cannot be shown as its resource. */
data class GatewaySkippedResource(
    val resourceType: String,
    val code: String,
    val reason: String,
    val id: String? = null,
)

/**
 * What to apply to a gateway. All fields are optional; [version] defaults to `latest`.
 *
 * @property selection The keys of the changes to apply; every other change is left alone.
 * @property skip The keys of changes to leave alone in this apply only.
 */
data class ApplyGatewayConfigurationRequest(
    val version: String? = null,
    val dryRun: Boolean? = null,
    val confirmRemovals: Boolean? = null,
    val selection: List<String>? = null,
    val skip: List<String>? = null,
)

/** Options of a revert. */
data class RevertGatewayConfigurationRequest(
    val confirmRemovals: Boolean? = null,
    val dryRun: Boolean? = null,
)

/** How applying a version would change a gateway. */
data class GatewayConfigurationDiff(
    val fromVersion: String? = null,
    val toVersion: String? = null,
    val summary: GatewayConfigurationDiffSummary? = null,
    val changes: List<GatewayConfigurationChange>? = null,
    val misplacedSecrets: List<ConfigurationValueReference>? = null,
)

/** The changes of a diff counted by kind. */
data class GatewayConfigurationDiffSummary(
    val added: Int? = null,
    val updated: Int? = null,
    val deleted: Int? = null,
    val unchanged: Int? = null,
)

/**
 * One resource's change in a diff.
 *
 * @property key Names the resource in an apply's `selection` or `skip`.
 * @property change One of `added`, `updated`, `deleted`, `unchanged`.
 */
data class GatewayConfigurationChange(
    val key: String? = null,
    val excluded: Boolean? = null,
    val skipped: Boolean? = null,
    val resourceType: String? = null,
    val id: String? = null,
    val name: String? = null,
    val change: String? = null,
    val lines: List<GatewayConfigurationDiffLine>? = null,
)

/** One line of a resource's document diff. [kind] is ` `, `+` or `-`. */
data class GatewayConfigurationDiffLine(
    val kind: String? = null,
    val text: String? = null,
)

/**
 * The result of an apply or a revert.
 *
 * @property import The gateway's own account of the import, as it answered.
 * @property missing Only on a dry run: the values the version refers to that the gateway does not hold.
 * @property changed The values set on the gateway again since the version it holds was applied. Names only.
 * @property queued Set when another apply was running, so the latest version will be applied after it.
 */
data class GatewayApplyResult(
    val gatewayId: String? = null,
    val dryRun: Boolean? = null,
    val diff: GatewayConfigurationDiff? = null,
    @SerializedName("import")
    val import: Map<String, Any?>? = null,
    val recorded: Boolean? = null,
    val missing: GatewayValueNames? = null,
    val changed: GatewayValueNames? = null,
    val queued: Boolean? = null,
    val queuedVersion: String? = null,
)

/** Variable and secret names a version refers to, and where it refers to each. Never values. */
data class GatewayValueNames(
    val variables: List<String>? = null,
    val secrets: List<String>? = null,
    val references: List<ConfigurationValueReference>? = null,
)

/**
 * One place a configuration version refers to a variable or a secret.
 *
 * @property kind `variable` or `secret`.
 * @property list Set when the reference is a list's item.
 */
data class ConfigurationValueReference(
    val name: String? = null,
    val kind: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val resourceName: String? = null,
    val field: String? = null,
    val list: Boolean? = null,
)

/**
 * What a gateway holds, resource by resource: every resource deployed to it, as the gateway last accepted it,
 * with how each one got there. Each apply writes a new master and keeps the one before. A gateway nothing was
 * applied to holds the empty master, [number] 0, without [entries] or [resources].
 *
 * @property number Orders the gateway's masters, from 1.
 * @property master The hash naming the master by what it holds. Absent for the empty master.
 * @property createdBy The user whose apply wrote the master, when the server can read the user's name.
 * @property resources Every resource the gateway holds, one YAML document each. Absent from a history listing.
 * @property entries One entry per resource key, sorted by key. Absent from a history listing.
 */
data class GatewayMaster(
    val gatewayId: String? = null,
    val number: Int? = null,
    val master: String? = null,
    val createdAt: String? = null,
    val createdBy: ConfigurationActor? = null,
    val resources: String? = null,
    val summary: GatewayMasterSummary? = null,
    val entries: List<GatewayMasterEntry>? = null,
)

/** A master's resources counted by state. */
data class GatewayMasterSummary(
    val deployed: Int? = null,
    val failed: Int? = null,
    val removed: Int? = null,
)

/**
 * One resource of a master.
 *
 * @property key The resource's key, `<resourceType>/<id>`, as a diff reports it.
 * @property contentHash The SHA-256 of the document as the master holds it. Absent for a removed resource or
 *   one the gateway never accepted.
 * @property sourceVersion The hash of the version the resource's last apply sent it from.
 * @property state `deployed`, `failed` (with [failure]) or `removed`.
 * @property at When the entry was last written.
 * @property by The user whose apply last wrote the entry, when the server can read the user's name.
 */
data class GatewayMasterEntry(
    val key: String? = null,
    val resourceType: String? = null,
    val id: String? = null,
    val name: String? = null,
    val contentHash: String? = null,
    val sourceVersion: String? = null,
    val origin: String? = null,
    val state: String? = null,
    val failure: GatewayMasterEntryFailure? = null,
    val at: String? = null,
    val by: ConfigurationActor? = null,
)

/** What a gateway answered when it refused a resource. */
data class GatewayMasterEntryFailure(
    val code: String? = null,
    val message: String? = null,
    val description: String? = null,
)

/** One resource's entry in each kept master that records it, newest first. */
data class GatewayResourceHistory(
    val gatewayId: String? = null,
    val key: String? = null,
    val history: List<GatewayResourceHistoryItem>? = null,
)

/** A resource's entry in one master, named by the master's hash and number. */
data class GatewayResourceHistoryItem(
    val master: String? = null,
    val number: Int? = null,
    val entry: GatewayMasterEntry? = null,
)
