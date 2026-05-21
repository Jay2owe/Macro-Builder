param(
    [string]$FijiDir = (Join-Path $env:GITHUB_WORKSPACE "Fiji.app"),
    [string]$JarPath,
    [string]$SiteName = "Macro-Builder",
    [string]$SiteUrl = "https://sites.imagej.net/Macro-Builder/",
    [string]$Username = "Jay2owe",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if (-not $JarPath) {
    $jars = Get-ChildItem -Path "target/Macro_Builder-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-tests.jar" } |
        Sort-Object LastWriteTime -Descending
    if (-not $jars) {
        throw "No uploadable Macro_Builder jar found under target/."
    }
    $JarPath = $jars[0].FullName
}

$jar = Get-Item -LiteralPath $JarPath
$password = $env:IMAGEJ_UPLOAD_PASSWORD
if (-not $password) {
    throw "IMAGEJ_UPLOAD_PASSWORD is required for the Fiji update-site upload workflow."
}

if ($env:IMAGEJ_UPLOAD_USER) {
    $Username = $env:IMAGEJ_UPLOAD_USER
}

$fiji = Get-Item -LiteralPath $FijiDir
$launcherNames = @("ImageJ-linux64", "ImageJ2-linux64", "fiji-linux-x64", "ImageJ-linux64.sh", "ImageJ2-linux64.sh")
$launcher = $null
foreach ($name in $launcherNames) {
    $candidate = Join-Path $fiji.FullName $name
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        $launcher = $candidate
        break
    }
}

if (-not $launcher) {
    $launcher = Get-ChildItem -LiteralPath $fiji.FullName -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "^ImageJ2?.*linux64" -or $_.Name -eq "fiji-linux-x64" } |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not $launcher) {
    $candidates = Get-ChildItem -LiteralPath $fiji.FullName -File -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Name
    throw "Fiji Linux launcher was not found in '$($fiji.FullName)'. Root files: $($candidates -join ', ')"
}

$pluginsDir = Join-Path $fiji.FullName "plugins"
New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null
Get-ChildItem -LiteralPath $pluginsDir -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "Macro_Builder-*.jar" -or $_.Name -like "Macro-Builder*.jar" } |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }

$destination = Join-Path $pluginsDir $jar.Name
Copy-Item -LiteralPath $jar.FullName -Destination $destination -Force

if ($env:RUNNER_OS -ne "Windows" -and (Get-Command chmod -ErrorAction SilentlyContinue)) {
    & chmod +x $launcher
}

$hostSpec = "webdav:{0}:{1}" -f $Username, $password

Write-Host "Configuring update site '$SiteName' at $SiteUrl"
& $launcher --update edit-update-site $SiteName $SiteUrl $hostSpec .
if ($LASTEXITCODE -ne 0) {
    throw "ImageJ updater failed while configuring update site '$SiteName'."
}

Write-Host "Refreshing local Fiji updater metadata"
& $launcher --update update
if ($LASTEXITCODE -ne 0) {
    throw "ImageJ updater failed while refreshing local metadata."
}

$relativeJar = "plugins/$($jar.Name)"
$uploadArgs = @("--update", "upload", "--update-site", $SiteName, "--force-shadow", "--forget-missing-dependencies", $relativeJar)
if ($DryRun) {
    $uploadArgs = @("--update", "upload", "--simulate") + $uploadArgs[2..($uploadArgs.Count - 1)]
    Write-Host "Running simulated upload for $relativeJar"
}
else {
    Write-Host "Uploading $relativeJar to $SiteName"
}

& $launcher @uploadArgs
if ($LASTEXITCODE -ne 0) {
    throw "ImageJ updater upload command failed."
}

if ($DryRun) {
    Write-Host "Dry run complete. No files were uploaded."
}
else {
    Write-Host "Upload complete."
}
