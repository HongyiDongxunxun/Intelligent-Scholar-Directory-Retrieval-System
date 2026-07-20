$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Frontend = Join-Path $Root "frontend"

Push-Location $Frontend
try {
    if (-not (Test-Path -LiteralPath (Join-Path $Frontend "node_modules"))) { & npm.cmd install }
    & npm.cmd run dev
} finally {
    Pop-Location
}
