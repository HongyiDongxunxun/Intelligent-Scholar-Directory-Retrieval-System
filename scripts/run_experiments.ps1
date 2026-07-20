param(
    [string]$BaseUrl = "http://127.0.0.1:8091",
    [int]$Workers = 30,
    [int]$Requests = 60
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Runner = Join-Path $Root "experiments\run_experiments.py"
$Python = Get-Command python.exe -ErrorAction SilentlyContinue

if ($Python) {
    & $Python.Source $Runner --base-url $BaseUrl --workers $Workers --requests $Requests
    exit $LASTEXITCODE
}

$PyLauncher = Get-Command py.exe -ErrorAction SilentlyContinue
if ($PyLauncher) {
    & $PyLauncher.Source -3 $Runner --base-url $BaseUrl --workers $Workers --requests $Requests
    exit $LASTEXITCODE
}

$Bundled = Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
if (Test-Path -LiteralPath $Bundled) {
    & $Bundled $Runner --base-url $BaseUrl --workers $Workers --requests $Requests
    exit $LASTEXITCODE
}

throw "Python 3 was not found. Install Python or add python.exe to PATH."
