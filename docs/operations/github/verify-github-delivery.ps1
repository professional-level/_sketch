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

function Assert-ImageExists {
    param([string] $Image)

    Write-Host "docker manifest inspect $Image"
    & docker manifest inspect $Image | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Image manifest was not found or could not be inspected: $Image"
    }
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
    return
}

$workflowRunsUri = "https://api.github.com/repos/$RepoFullName/actions/runs?head_sha=$CommitSha&per_page=100"
$workflowRunsResponse = Invoke-GitHubApi $workflowRunsUri
$runs = @($workflowRunsResponse.workflow_runs)

foreach ($workflow in $ExpectedWorkflowNames) {
    Assert-WorkflowSucceeded $runs $workflow
}

if (-not $SkipRegistryCheck) {
    Require-Command "docker"
    $owner = $RepoFullName.Split("/")[0]
    foreach ($image in $ExpectedImages) {
        Assert-ImageExists ("ghcr.io/{0}/sketch-{1}:{2}" -f $owner, $image, $CommitSha)
    }
}

Write-Host "GitHub delivery verification passed."
