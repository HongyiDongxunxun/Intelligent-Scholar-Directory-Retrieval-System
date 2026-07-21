param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Encrypt", "Decrypt")]
    [string]$Action,
    [string]$SchemaPath,
    [string]$ProjectConfigPath,
    [string]$ExperimentConfigPath,
    [string]$LocalConfigPath,
    [string]$PomPath,
    [string]$BundlePath = ".\private-materials\original-build-materials.isdenc",
    [string]$OutputDirectory = ".\private-materials\restored"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Tool = Join-Path $PSScriptRoot "PrivateBundleCrypto.java"

if ($Action -eq "Encrypt") {
    $Inputs = @(
        @{ Name = "database/schema.sql"; Path = $SchemaPath },
        @{ Name = "config/application.yml"; Path = $ProjectConfigPath },
        @{ Name = "config/application-experiment.yml"; Path = $ExperimentConfigPath },
        @{ Name = "config/application-localdb.yml"; Path = $LocalConfigPath },
        @{ Name = "build/pom.xml"; Path = $PomPath }
    )
    foreach ($Input in $Inputs) {
        if ([string]::IsNullOrWhiteSpace($Input.Path) -or -not (Test-Path -LiteralPath $Input.Path -PathType Leaf)) {
            throw "Missing private input for $($Input.Name)"
        }
    }
    $ResolvedBundle = [System.IO.Path]::GetFullPath((Join-Path $Root $BundlePath))
    $Arguments = @($Tool, "encrypt", $ResolvedBundle)
    $Arguments += $Inputs | ForEach-Object { "$($_.Name)=$([System.IO.Path]::GetFullPath($_.Path))" }
    & java @Arguments
    exit $LASTEXITCODE
}

$ResolvedBundle = [System.IO.Path]::GetFullPath((Join-Path $Root $BundlePath))
$ResolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $Root $OutputDirectory))
& java $Tool decrypt $ResolvedBundle $ResolvedOutput
exit $LASTEXITCODE
