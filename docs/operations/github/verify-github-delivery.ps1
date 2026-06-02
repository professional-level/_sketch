[CmdletBinding()]
param(
    [string] $RepoFullName = "professional-level/_sketch",

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-fA-F]{7,40}$")]
    [string] $CommitSha,

    [string[]] $ExpectedWorkflowNames = @(
        "Container Images",
        "Operations Validation"
    ),

    [string[]] $ExpectedImages = @(
        "kis-wrapper",
        "stock-search-service",
        "strategy-execution-service",
        "stock-purchase-service"
    ),

    [switch] $SkipRegistryCheck,

    [ValidateSet("Auto", "Docker", "Http")]
    [string] $RegistryCheckMode = "Auto",

    [switch] $DryRun
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

function Get-GitHubHeaders {
    $headers = @{
        "Accept" = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
    }

    if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_TOKEN)) {
        $headers["Authorization"] = "Bearer $env:GITHUB_TOKEN"
    }

    return $headers
}

function Invoke-GitHubApi {
    param([string] $Uri)

    Write-Host "GET $Uri"
    Invoke-RestMethod -Method Get -Uri $Uri -Headers (Get-GitHubHeaders)
}

function Require-Command {
    param([string] $Name)

    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found on PATH."
    }
}

function Test-CommandExists {
    param([string] $Name)

    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-HeaderValue {
    param(
        [object] $Response,
        [string] $Name
    )

    if ($null -eq $Response -or $null -eq $Response.Headers) {
        return $null
    }

    $value = $Response.Headers[$Name]
    if ($null -eq $value) {
        return $null
    }

    if ($value -is [array]) {
        return ($value -join ",")
    }

    return [string] $value
}

function Get-ResponseStatusCode {
    param([object] $ErrorRecord)

    if ($null -eq $ErrorRecord.Exception.Response) {
        return $null
    }

    return [int] $ErrorRecord.Exception.Response.StatusCode
}

function ConvertTo-BearerChallenge {
    param([string] $Header)

    if ([string]::IsNullOrWhiteSpace($Header) -or -not $Header.TrimStart().StartsWith("Bearer ")) {
        throw "Registry did not return a Bearer authentication challenge."
    }

    $challenge = @{}
    $matches = [regex]::Matches($Header, '(\w+)="([^"]*)"')
    foreach ($match in $matches) {
        $challenge[$match.Groups[1].Value] = $match.Groups[2].Value
    }

    if (-not $challenge.ContainsKey("realm")) {
        throw "Registry Bearer authentication challenge is missing realm."
    }

    return $challenge
}

function Get-GhcrCredentialHeaders {
    $token = $env:GHCR_TOKEN
    if ([string]::IsNullOrWhiteSpace($token)) {
        $token = $env:GITHUB_TOKEN
    }

    $username = $env:GHCR_USERNAME
    if ([string]::IsNullOrWhiteSpace($username)) {
        $username = $env:GITHUB_ACTOR
    }

    if ([string]::IsNullOrWhiteSpace($token) -or [string]::IsNullOrWhiteSpace($username)) {
        return @{}
    }

    $encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${username}:${token}"))
    return @{ "Authorization" = "Basic $encoded" }
}

function Get-GhcrBearerToken {
    param([hashtable] $Challenge)

    $query = @()
    foreach ($name in @("service", "scope")) {
        if ($Challenge.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace($Challenge[$name])) {
            $query += ("{0}={1}" -f $name, [uri]::EscapeDataString($Challenge[$name]))
        }
    }

    $tokenUri = $Challenge["realm"]
    if ($query.Count -gt 0) {
        $tokenUri = "${tokenUri}?$($query -join '&')"
    }

    Write-Host "GET $tokenUri"
    try {
        $response = Invoke-RestMethod -Method Get -Uri $tokenUri -Headers (Get-GhcrCredentialHeaders)
    }
    catch {
        $statusCode = Get-ResponseStatusCode $_
        throw "GHCR bearer token request failed. Set GHCR_USERNAME and GHCR_TOKEN, or GITHUB_ACTOR and GITHUB_TOKEN, with packages read access. status=$statusCode"
    }

    $tokenProperty = $response.PSObject.Properties["token"]
    if ($null -ne $tokenProperty -and -not [string]::IsNullOrWhiteSpace($tokenProperty.Value)) {
        return $tokenProperty.Value
    }

    $accessTokenProperty = $response.PSObject.Properties["access_token"]
    if ($null -ne $accessTokenProperty -and -not [string]::IsNullOrWhiteSpace($accessTokenProperty.Value)) {
        return $accessTokenProperty.Value
    }

    throw "GHCR token endpoint did not return a bearer token."
}

function Get-RegistryManifestHeaders {
    return @{
        "Accept" = @(
            "application/vnd.oci.image.index.v1+json",
            "application/vnd.docker.distribution.manifest.list.v2+json",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.docker.distribution.manifest.v2+json"
        ) -join ","
    }
}

function Assert-WorkflowSucceeded {
    param(
        [object[]] $Runs,
        [string] $WorkflowName
    )

    $matchingRun = @($Runs | Where-Object { $_.name -eq $WorkflowName } | Sort-Object created_at -Descending | Select-Object -First 1)
    if ($matchingRun.Count -eq 0) {
        throw "No workflow run found for '$WorkflowName' at commit $CommitSha."
    }

    $run = $matchingRun[0]
    if ($run.status -ne "completed" -or $run.conclusion -ne "success") {
        throw "Workflow '$WorkflowName' is not successful. status=$($run.status), conclusion=$($run.conclusion), url=$($run.html_url)"
    }

    Write-Host "Workflow '$WorkflowName' succeeded: $($run.html_url)"
}

function Assert-ImageExistsWithDocker {
    param([string] $Image)

    Write-Host "docker manifest inspect $Image"
    & docker manifest inspect $Image | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Image manifest was not found or could not be inspected: $Image"
    }
}

function Assert-ImageExistsWithHttp {
    param(
        [string] $Owner,
        [string] $Image,
        [string] $Reference
    )

    $repository = "$Owner/sketch-$Image"
    $manifestUri = "https://ghcr.io/v2/$repository/manifests/$Reference"
    $headers = Get-RegistryManifestHeaders

    Write-Host "GET $manifestUri"
    try {
        Invoke-WebRequest -Method Get -Uri $manifestUri -Headers $headers | Out-Null
        Write-Host "Image manifest exists: ghcr.io/${repository}:$Reference"
        return
    }
    catch {
        $statusCode = Get-ResponseStatusCode $_
        if ($statusCode -ne 401) {
            throw "Image manifest was not found or could not be inspected: ghcr.io/${repository}:$Reference status=$statusCode"
        }

        $challenge = ConvertTo-BearerChallenge (Get-HeaderValue $_.Exception.Response "WWW-Authenticate")
        $headers["Authorization"] = "Bearer $(Get-GhcrBearerToken $challenge)"
    }

    Write-Host "GET $manifestUri"
    try {
        Invoke-WebRequest -Method Get -Uri $manifestUri -Headers $headers | Out-Null
        Write-Host "Image manifest exists: ghcr.io/${repository}:$Reference"
    }
    catch {
        $statusCode = Get-ResponseStatusCode $_
        throw "Image manifest was not found or could not be inspected: ghcr.io/${repository}:$Reference status=$statusCode"
    }
}

function Resolve-RegistryCheckMode {
    param([string] $Mode)

    if ($Mode -ne "Auto") {
        return $Mode
    }

    if (Test-CommandExists "docker") {
        return "Docker"
    }

    return "Http"
}

if ($DryRun) {
    Write-Host "Would verify GitHub Actions for $RepoFullName at $CommitSha"
    foreach ($workflow in $ExpectedWorkflowNames) {
        Write-Host "Expected workflow: $workflow"
    }
    foreach ($image in $ExpectedImages) {
        Write-Host ("Expected GHCR image: ghcr.io/{0}/sketch-{1}:{2}" -f $RepoFullName.Split("/")[0], $image, $CommitSha)
    }
    if ($SkipRegistryCheck) {
        Write-Host "Registry checks would be skipped."
    }
    else {
        Write-Host "Registry check mode: $RegistryCheckMode"
    }
    return
}

$workflowRunsUri = "https://api.github.com/repos/$RepoFullName/actions/runs?head_sha=$CommitSha&per_page=100"
$workflowRunsResponse = Invoke-GitHubApi $workflowRunsUri
$runs = @($workflowRunsResponse.workflow_runs)

foreach ($workflow in $ExpectedWorkflowNames) {
    Assert-WorkflowSucceeded $runs $workflow
}

if (-not $SkipRegistryCheck) {
    $owner = $RepoFullName.Split("/")[0]
    $resolvedRegistryCheckMode = Resolve-RegistryCheckMode $RegistryCheckMode
    Write-Host "Registry check mode: $resolvedRegistryCheckMode"
    if ($resolvedRegistryCheckMode -eq "Docker") {
        Require-Command "docker"
    }

    foreach ($image in $ExpectedImages) {
        if ($resolvedRegistryCheckMode -eq "Docker") {
            Assert-ImageExistsWithDocker ("ghcr.io/{0}/sketch-{1}:{2}" -f $owner, $image, $CommitSha)
        }
        else {
            Assert-ImageExistsWithHttp $owner $image $CommitSha
        }
    }
}

Write-Host "GitHub delivery verification passed."
