// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/**
 * A captured version of the deployment's configuration.
 *
 * @property version The SHA-256 of the captured documents, in hex.
 * @property resources The exported configuration, one document per resource. Left out of a listing.
 * @property references Each place the version refers to a variable or a secret. Only a read of one version
 *   carries it.
 * @property unchanged Only in a capture's answer, when the configuration matched an existing version.
 * @property skipped Only in a capture's answer: the resources the export could not write.
 */
data class ConfigurationVersion(
    val number: Int? = null,
    val version: String? = null,
    val name: String? = null,
    val note: String? = null,
    val unchanged: Boolean? = null,
    val current: Boolean? = null,
    val createdAt: String? = null,
    val createdBy: ConfigurationActor? = null,
    val resources: String? = null,
    val references: List<ConfigurationValueReference>? = null,
    val skipped: List<ConfigurationSkippedResource>? = null,
)

/** A resource the export could not write, left out of a captured version, with why. */
data class ConfigurationSkippedResource(
    val resourceType: String? = null,
    val resourceId: String? = null,
    val error: String? = null,
    val code: String? = null,
)

/**
 * The options of a capture.
 *
 * @property current Keep the configuration as the current version rather than adding one.
 */
data class CaptureConfigurationVersionRequest(
    val name: String? = null,
    val note: String? = null,
    val current: Boolean? = null,
)

/**
 * Which resources to export. Each list holds resource identifiers, or `["*"]` for every resource of that
 * type.
 */
data class ExportConfigurationRequest(
    val applications: List<String>? = null,
    val connections: List<String>? = null,
    val userTypes: List<String>? = null,
    val agentTypes: List<String>? = null,
    val organizationUnits: List<String>? = null,
    val users: List<String>? = null,
    val groups: List<String>? = null,
    val resourceServers: List<String>? = null,
    val authorizationDetailTypes: List<String>? = null,
    val roles: List<String>? = null,
    val flows: List<String>? = null,
    val translations: List<String>? = null,
    val layouts: List<String>? = null,
    val themes: List<String>? = null,
    val credentialConfigurations: List<String>? = null,
    val presentationDefinitions: List<String>? = null,
    val notificationTemplates: List<String>? = null,
    val options: ExportOptions? = null,
)

/** Options of an export. [format] is `yaml`, the only one supported. */
data class ExportOptions(
    val includeMetadata: Boolean? = null,
    val includeDependencies: Boolean? = null,
    val format: String? = null,
    val folderStructure: ExportFolderStructureOptions? = null,
    val pagination: ExportPaginationOptions? = null,
)

/** How exported files are organized. */
data class ExportFolderStructureOptions(
    val groupByType: Boolean? = null,
    val customStructure: Map<String, String>? = null,
    val fileNamingPattern: String? = null,
)

/** Paging of a bulk export. [page] starts at 1. */
data class ExportPaginationOptions(
    val page: Int? = null,
    val limit: Int? = null,
)

/**
 * An export: the combined YAML of the exported resources, and the `.env` content naming the template
 * variables they use (empty when there are none).
 */
data class ExportConfigurationResponse(
    val resources: String,
    @SerializedName("environment_variables")
    val environmentVariables: String,
)

/**
 * What to import: YAML documents in [content] (separated by `---`) to create or update, [deletions] to
 * remove, or both.
 *
 * @property variables Values for the `{{.NAME}}` template variables in [content]. They can hold
 *   credentials, so they are left out of [toString].
 */
data class ImportConfigurationRequest(
    val content: String? = null,
    val variables: Map<String, Any?>? = null,
    val dryRun: Boolean? = null,
    val deletions: List<ImportResourceDeletion>? = null,
    val options: ImportOptions? = null,
) {
    override fun toString(): String =
        "ImportConfigurationRequest(content=${content?.length?.let { "<$it chars>" }}, " +
            "variables=${variables?.keys}, dryRun=$dryRun, deletions=$deletions, options=$options)"
}

/** A runtime resource to remove during an import. */
data class ImportResourceDeletion(
    val resourceType: String,
    val id: String,
)

/** Options of an import. [target] is `runtime`, the only one supported. */
data class ImportOptions(
    val upsert: Boolean? = null,
    val continueOnError: Boolean? = null,
    val target: String? = null,
)

/** The outcome of an import. */
data class ImportConfigurationResponse(
    val summary: ImportSummary,
    val results: List<ImportItemOutcome>,
)

/** The counts of an import. */
data class ImportSummary(
    val totalDocuments: Int,
    val imported: Int,
    val failed: Int,
    val importedAt: String,
    val deleted: Int? = null,
)

/**
 * The outcome of one imported document.
 *
 * @property operation `create` or `update`, when it succeeded.
 * @property status `success` or `failed`.
 */
data class ImportItemOutcome(
    val resourceType: String,
    val status: String,
    val resourceId: String? = null,
    val resourceName: String? = null,
    val operation: String? = null,
    val code: String? = null,
    val message: String? = null,
)

/** Identifies a file-backed declarative resource to delete. */
data class DeleteImportedConfigurationRequest(
    val resourceType: String,
    val resourceKey: String,
)

/** Confirms the deletion of a declarative resource file. */
data class DeleteImportedConfigurationResponse(
    val resourceType: String,
    val resourceKey: String,
    val deletedFile: String,
)
