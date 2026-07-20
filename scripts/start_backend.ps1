$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "backend"
$Jar = Join-Path $Backend "target\scholar-directory-api-0.1.0.jar"

if (-not (Test-Path -LiteralPath $Jar)) {
    Push-Location $Backend
    try { & mvn.cmd package } finally { Pop-Location }
}

& java -jar $Jar
