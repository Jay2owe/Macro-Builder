param(
    [string]$FijiPluginsDir,
    [switch]$CopyOnly
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

if (-not $CopyOnly) {
    Push-Location $root
    try {
        & .\mvnw.cmd clean -DskipTests "-Denforcer.skip=true" package
    }
    finally {
        Pop-Location
    }
}

$jar = Get-ChildItem -Path (Join-Path $root "target\Macro_Builder-*.jar") -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-tests.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "Plugin jar was not found under '$root\target'. Run without -CopyOnly to build it first."
}

if ($FijiPluginsDir) {
    if (-not (Test-Path -LiteralPath $FijiPluginsDir -PathType Container)) {
        throw "Fiji plugins directory does not exist: '$FijiPluginsDir'"
    }

    Copy-Item -LiteralPath $jar.FullName -Destination $FijiPluginsDir -Force
    Write-Host "Copied plugin jar to $FijiPluginsDir"
}
else {
    Write-Host "Built plugin jar at $($jar.FullName)"
    Write-Host "Pass -FijiPluginsDir '<path-to-Fiji.app\plugins>' to install it for Fiji testing."
}
