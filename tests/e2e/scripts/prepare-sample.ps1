<#
.SYNOPSIS
    Fetch the Android Quickstart sample and point it at this SDK checkout.

.DESCRIPTION
    Copies or clones the Android Quickstart sample from the ThunderID samples repository into
    tests\e2e\.samples and points it at this SDK checkout instead of the published release, so
    the E2E suite exercises the code under test. The PowerShell counterpart of prepare-sample.sh.
    Writes the prepared sample directory to the pipeline.

    Every run starts from a fresh copy, so re-running is safe.

.NOTES
    Environment variables, all optional:
      THUNDERID_SAMPLES_DIR   Existing local samples checkout to copy the sample from (no clone)
      THUNDERID_SAMPLES_REPO  Samples repository to clone (default https://github.com/thunder-id/samples.git)
      THUNDERID_SAMPLES_REF   Branch, tag or commit to clone (default main)
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir '..\..\..')).Path
$SamplesDir = Join-Path $ScriptDir '..\.samples'
# The quickstart's path in the samples repository. A samples checkout from before the quickstarts
# were renamed has it at the bare platform name, so an older THUNDERID_SAMPLES_REF still works.
$SamplePaths = @('quickstarts/quickstart-android', 'quickstarts/android')
$SamplesRepo = if ($env:THUNDERID_SAMPLES_REPO) { $env:THUNDERID_SAMPLES_REPO } else { 'https://github.com/thunder-id/samples.git' }
$SamplesRef = if ($env:THUNDERID_SAMPLES_REF) { $env:THUNDERID_SAMPLES_REF } else { 'main' }

# Returns the first of $SamplePaths that exists under the given samples checkout.
function Find-Sample([string]$Root, [string]$Description) {
    foreach ($path in $SamplePaths) {
        if (Test-Path (Join-Path $Root $path)) { return $path }
    }
    throw "None of $($SamplePaths -join ', ') exists in $Description."
}

# The published artifact the sample depends on, substituted with this checkout's root project.
$SdkModule = 'com.github.thunder-id:android-sdks'

if (Test-Path $SamplesDir) { Remove-Item -Recurse -Force $SamplesDir }
New-Item -ItemType Directory -Force -Path $SamplesDir | Out-Null

if ($env:THUNDERID_SAMPLES_DIR) {
    $SamplePath = Find-Sample $env:THUNDERID_SAMPLES_DIR $env:THUNDERID_SAMPLES_DIR
    $source = Join-Path $env:THUNDERID_SAMPLES_DIR $SamplePath
    Write-Host "==> Copying $SamplePath from $env:THUNDERID_SAMPLES_DIR"
    $target = Join-Path $SamplesDir $SamplePath
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -Recurse -Path $source -Destination $target
    # Drop local build state and configuration a working checkout may carry.
    foreach ($item in 'build', '.gradle', 'local.properties', 'config.properties') {
        $path = Join-Path $target $item
        if (Test-Path $path) { Remove-Item -Recurse -Force $path }
    }
} else {
    Write-Host "==> Fetching the quickstart from $SamplesRepo at $SamplesRef"
    # init + fetch rather than `clone --branch`, so a commit SHA works as the ref too.
    & git -C $SamplesDir init -q
    & git -C $SamplesDir remote add origin $SamplesRepo
    & git -C $SamplesDir fetch -q --depth 1 origin $SamplesRef
    if ($LASTEXITCODE -ne 0) { throw "Fetching $SamplesRef from $SamplesRepo failed." }
    & git -C $SamplesDir checkout -q FETCH_HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Checking out the samples repository failed.' }
    $SamplePath = Find-Sample $SamplesDir "$SamplesRepo at $SamplesRef"
}

$SampleDir = (Resolve-Path (Join-Path $SamplesDir $SamplePath)).Path

if (-not (Select-String -Path (Join-Path $SampleDir 'build.gradle.kts') -SimpleMatch "`"${SdkModule}:" -Quiet)) {
    throw "$SampleDir\build.gradle.kts does not depend on $SdkModule."
}

Write-Host "==> Pointing the sample at the local SDK in $RepoRoot"
# Gradle accepts forward slashes on Windows, and they need no escaping in a Kotlin string.
$gradleRoot = $RepoRoot -replace '\\', '/'
@"

// Added by tests/e2e/scripts/prepare-sample.ps1: build against the local SDK checkout.
includeBuild("$gradleRoot") {
    name = "android"
    dependencySubstitution {
        substitute(module("$SdkModule")).using(project(":"))
    }
}
"@ | Add-Content -Path (Join-Path $SampleDir 'settings.gradle.kts') -Encoding utf8

$SampleDir
