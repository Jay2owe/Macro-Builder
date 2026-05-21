param(
    [string]$SiteUrl = "https://sites.imagej.net/Macro-Builder/",
    [string]$Username = "Jay2owe",
    [string]$Password,
    [switch]$CheckOnly,
    [switch]$RequireAbsent
)

$ErrorActionPreference = "Stop"

function Join-Url {
    param(
        [string]$BaseUrl,
        [string]$Child
    )

    return $BaseUrl.TrimEnd("/") + "/" + $Child.TrimStart("/")
}

function Get-HttpStatus {
    param([string]$Url)

    try {
        $response = Invoke-WebRequest -Uri $Url -Method Head -UseBasicParsing -TimeoutSec 30
        return [int]$response.StatusCode
    }
    catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

$lockUrl = Join-Url -BaseUrl $SiteUrl -Child "db.xml.gz.lock"
$status = Get-HttpStatus -Url $lockUrl

if ($status -eq 404) {
    Write-Host "No remote lock exists at $lockUrl"
    exit 0
}

if ($status -ne 200) {
    throw "Unexpected status for '$lockUrl': HTTP $status"
}

Write-Host "Remote lock exists at $lockUrl"

if ($RequireAbsent) {
    throw "Remote lock exists at $lockUrl. Clear it before uploading."
}

if ($CheckOnly) {
    exit 0
}

if (-not $Password) {
    $Password = $env:IMAGEJ_UPLOAD_PASSWORD
}

if (-not $Password) {
    $credential = Get-Credential -UserName $Username -Message "ImageJ WebDAV upload credentials for $SiteUrl"
    $networkCredential = $credential.GetNetworkCredential()
    $Username = $networkCredential.UserName
    $Password = $networkCredential.Password
}

$pair = "{0}:{1}" -f $Username, $Password
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $basic" }

try {
    $deleteResponse = Invoke-WebRequest -Uri $lockUrl -Method Delete -Headers $headers -UseBasicParsing -TimeoutSec 30
    Write-Host "Delete returned HTTP $($deleteResponse.StatusCode)"
}
catch {
    if ($_.Exception.Response) {
        $code = [int]$_.Exception.Response.StatusCode
        throw "Delete failed with HTTP $code. Check the WebDAV username/password and update-site upload permissions."
    }
    throw
}

$after = Get-HttpStatus -Url $lockUrl
if ($after -eq 404) {
    Write-Host "Remote lock cleared."
    exit 0
}

throw "Remote lock still exists after delete attempt. Current status: HTTP $after"
