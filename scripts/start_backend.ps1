param(
    [string]$ServerAddress = $env:SERVER_ADDRESS,
    [string]$ApiToken = $env:API_ACCESS_TOKEN
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "backend"
$Jar = Join-Path $Backend "target\scholar-directory-api-0.1.0.jar"

if ([string]::IsNullOrWhiteSpace($ServerAddress)) { $ServerAddress = "127.0.0.1" }
if ($ServerAddress -notin @("127.0.0.1", "localhost", "::1") -and [string]::IsNullOrWhiteSpace($ApiToken)) {
    throw "A non-loopback ServerAddress requires ApiToken. Prefer a TLS reverse proxy for any shared deployment."
}

if (-not (Test-Path -LiteralPath $Jar)) {
    Push-Location $Backend
    try { & mvn.cmd package } finally { Pop-Location }
}

$PreviousAddress = $env:SERVER_ADDRESS
$PreviousToken = $env:API_ACCESS_TOKEN
try {
    $env:SERVER_ADDRESS = $ServerAddress
    if (-not [string]::IsNullOrWhiteSpace($ApiToken)) { $env:API_ACCESS_TOKEN = $ApiToken }
    & java -jar $Jar
} finally {
    $env:SERVER_ADDRESS = $PreviousAddress
    $env:API_ACCESS_TOKEN = $PreviousToken
}
