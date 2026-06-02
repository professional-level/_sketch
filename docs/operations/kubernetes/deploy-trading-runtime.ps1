[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$")]
    [string] $ImageTag,

    [string] $Namespace = "akra-trading",

    [string] $ManifestPath = (Join-Path $PSScriptRoot "trading-runtime.yaml"),

    [string] $InfraManifestPath,

    [string] $InfraNamespace = "trading-infra",

    [string] $SecretManifestPath,

    [string] $SqlDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) "sql"),

    [int] $MigrationTimeoutSeconds = 300,

    [int] $RolloutTimeoutSeconds = 300,

    [int] $InfraTimeoutSeconds = 600,

    [switch] $DryRun,

    [switch] $SkipMigrations,

    [switch] $SkipRolloutStatus,

    [switch] $SkipInfraStatus,

    [switch] $AllowTemplatePlaceholders
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

function Require-Command {
    param([string] $Name)

    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found on PATH."
    }
}

function Invoke-Kubectl {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)

    Write-Host ("kubectl {0}" -f ($Arguments -join " "))
    & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl failed with exit code $LASTEXITCODE"
    }
}

function Invoke-KubectlPipe {
    param(
        [string[]] $LeftArguments,
        [string[]] $RightArguments
    )

    Write-Host ("kubectl {0} | kubectl {1}" -f (($LeftArguments -join " "), ($RightArguments -join " ")))
    $output = & kubectl @LeftArguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl failed with exit code $LASTEXITCODE"
    }

    $output | & kubectl @RightArguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl failed with exit code $LASTEXITCODE"
    }
}

function New-RenderedManifest {
    param(
        [string] $Path,
        [string] $RenderedImageTag
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Manifest was not found: $Path"
    }

    $content = Get-Content -LiteralPath $Path -Raw
    $rendered = $content.Replace("REPLACE_IMAGE_TAG", $RenderedImageTag)
    $remainingPlaceholders = [regex]::Matches($rendered, "REPLACE_[A-Z0-9_]+") |
        ForEach-Object { $_.Value } |
        Sort-Object -Unique

    if ($remainingPlaceholders.Count -gt 0 -and -not ($DryRun -and $AllowTemplatePlaceholders)) {
        $joined = $remainingPlaceholders -join ", "
        throw "Manifest still contains deployment placeholders: $joined. Use a prepared manifest with real values, or use -DryRun -AllowTemplatePlaceholders for template validation only."
    }

    $tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ("akra-trading-runtime-{0}.yaml" -f [Guid]::NewGuid())
    Set-Content -LiteralPath $tempPath -Value $rendered -Encoding UTF8
    return $tempPath
}

Require-Command "kubectl"

if (-not $SkipMigrations -and -not (Test-Path -LiteralPath $SqlDirectory)) {
    throw "SQL migration directory was not found: $SqlDirectory"
}

$tempManifests = @()

try {
    if (-not [string]::IsNullOrWhiteSpace($InfraManifestPath)) {
        $tempInfraManifest = New-RenderedManifest $InfraManifestPath $ImageTag
        $tempManifests += $tempInfraManifest
        $infraApplyArgs = @("apply", "-f", $tempInfraManifest)
        if ($DryRun) {
            $infraApplyArgs += "--dry-run=client"
        }
        Invoke-Kubectl @infraApplyArgs

        if (-not $DryRun -and -not $SkipInfraStatus) {
            foreach ($statefulSet in @("mysql", "zookeeper", "kafka", "temporal-postgresql")) {
                Invoke-Kubectl "rollout" "status" "statefulset/$statefulSet" "-n" $InfraNamespace "--timeout=$($InfraTimeoutSeconds)s"
            }

            Invoke-Kubectl "wait" "-n" $InfraNamespace "--for=condition=complete" "job/akra-kafka-topic-bootstrap" "--timeout=$($InfraTimeoutSeconds)s"

            foreach ($deployment in @("temporal", "temporal-ui")) {
                Invoke-Kubectl "rollout" "status" "deployment/$deployment" "-n" $InfraNamespace "--timeout=$($InfraTimeoutSeconds)s"
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($SecretManifestPath)) {
        $tempSecretManifest = New-RenderedManifest $SecretManifestPath $ImageTag
        $tempManifests += $tempSecretManifest
        $secretApplyArgs = @("apply", "-f", $tempSecretManifest)
        if ($DryRun) {
            $secretApplyArgs += "--dry-run=client"
        }
        Invoke-Kubectl @secretApplyArgs
    }

    $tempManifest = New-RenderedManifest $ManifestPath $ImageTag
    $tempManifests += $tempManifest

    $applyArgs = @("apply", "-f", $tempManifest)
    if ($DryRun) {
        $applyArgs += "--dry-run=client"
    }
    Invoke-Kubectl @applyArgs

    if (-not $SkipMigrations) {
        $createMigrationConfigMap = @(
            "create",
            "configmap",
            "akra-sql-migrations",
            "-n",
            $Namespace,
            "--from-file=$SqlDirectory",
            "--dry-run=client",
            "-o",
            "yaml"
        )
        $applyMigrationConfigMap = @("apply", "-f", "-")
        if ($DryRun) {
            $applyMigrationConfigMap += "--dry-run=client"
        }
        Invoke-KubectlPipe $createMigrationConfigMap $applyMigrationConfigMap
    }

    if ($DryRun) {
        Write-Host "Dry run complete. Skipping migration job execution and rollout waits."
        return
    }

    if (-not $SkipMigrations) {
        Invoke-Kubectl "delete" "job" "akra-trading-schema-migration" "-n" $Namespace "--ignore-not-found=true"
        Invoke-Kubectl "apply" "-f" $tempManifest
        Invoke-Kubectl "patch" "job" "akra-trading-schema-migration" "-n" $Namespace "--type" "merge" "-p" '{"spec":{"suspend":false}}'
        Invoke-Kubectl "wait" "-n" $Namespace "--for=condition=complete" "job/akra-trading-schema-migration" "--timeout=$($MigrationTimeoutSeconds)s"
    }

    $deployments = @(
        "kis-wrapper",
        "stock-search-service",
        "strategy-execution-service",
        "stock-purchase-service"
    )

    foreach ($deployment in $deployments) {
        Invoke-Kubectl "rollout" "restart" "deployment/$deployment" "-n" $Namespace
    }

    if (-not $SkipRolloutStatus) {
        foreach ($deployment in $deployments) {
            Invoke-Kubectl "rollout" "status" "deployment/$deployment" "-n" $Namespace "--timeout=$($RolloutTimeoutSeconds)s"
        }
    }
}
finally {
    foreach ($manifest in $tempManifests) {
        Remove-Item -LiteralPath $manifest -Force -ErrorAction SilentlyContinue
    }
}
