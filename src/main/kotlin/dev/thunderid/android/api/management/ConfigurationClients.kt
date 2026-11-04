// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Configuration version operations, reached through `ThunderIDClient.configurationVersions`, against the
 * `configurationVersions` collection. Applying a version to a gateway is on `ThunderIDClient.gateways`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ConfigurationVersionsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns the captured versions, newest first, without their content. */
    suspend fun list(fetcher: ThunderIDFetcher? = null): List<ConfigurationVersion> =
        transport.request<Array<ConfigurationVersion>>("GET", fetcher = fetcher).toList()

    /**
     * Returns a version with its content.
     *
     * @param version A version hash, a prefix of at least seven characters, `latest`, or `current` for the
     *   configuration as it stands now.
     */
    suspend fun get(
        version: String,
        fetcher: ThunderIDFetcher? = null,
    ): ConfigurationVersion = transport.get(version, fetcher = fetcher)

    /**
     * Captures the configuration as it is now as a new version and returns it. A configuration that matches
     * an existing version returns that version, marked `unchanged`.
     */
    suspend fun create(
        request: CaptureConfigurationVersionRequest? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ConfigurationVersion = transport.request("POST", body = request, fetcher = fetcher)
}

/**
 * Configuration import and export, reached through `ThunderIDClient.configuration`, against the `import`
 * and `export` collections. Import template variables can hold credentials; the SDK never logs them. Each
 * call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ConfigurationClient internal constructor(
    private val importTransport: ManagementTransport,
    private val exportTransport: ManagementTransport,
) {
    /** Exports the requested resources as combined YAML, with the `.env` content of their template variables. */
    suspend fun exportConfiguration(
        request: ExportConfigurationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ExportConfigurationResponse = exportTransport.create(request, fetcher)

    /** Imports declarative YAML documents, and removes the listed resources, and returns each outcome. */
    suspend fun importConfiguration(
        request: ImportConfigurationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ImportConfigurationResponse = importTransport.create(request, fetcher)

    /** Deletes a file-backed declarative resource, sent to `{import}/delete`. */
    suspend fun deleteImportedConfiguration(
        request: DeleteImportedConfigurationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): DeleteImportedConfigurationResponse =
        importTransport.request(
            "POST",
            listOf("delete"),
            body = request,
            fetcher = fetcher,
        )
}
