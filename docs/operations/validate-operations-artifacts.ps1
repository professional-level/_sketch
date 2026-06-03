[CmdletBinding()]
param()

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

function Assert-FileExists {
    param([string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required file is missing: $Path"
    }
}

function Assert-Contains {
    param(
        [string] $Name,
        [string] $Content,
        [string] $Expected
    )

    if (-not $Content.Contains($Expected)) {
        throw "$Name does not contain expected text: $Expected"
    }
}

function Assert-NoReplacePlaceholders {
    param(
        [string] $Name,
        [string] $Content
    )

    $matches = @([regex]::Matches($Content, "REPLACE_[A-Z0-9_]+") | ForEach-Object { $_.Value } | Sort-Object -Unique)
    if ($matches.Count -gt 0) {
        throw "$Name still contains placeholders: $($matches -join ', ')"
    }
}

function Assert-PowerShellParses {
    param([string] $Path)

    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($Path, [ref] $tokens, [ref] $errors) | Out-Null
    if (@($errors).Count -gt 0) {
        $formatted = ($errors | ForEach-Object { $_.Message }) -join "; "
        throw "PowerShell parse failed for ${Path}: $formatted"
    }
}

function Replace-All {
    param(
        [string] $Content,
        [hashtable] $Replacements
    )

    $result = $Content
    foreach ($entry in $Replacements.GetEnumerator()) {
        $result = $result.Replace($entry.Key, $entry.Value)
    }
    return $result
}

function Assert-KubernetesDocumentShape {
    param(
        [string] $Name,
        [string] $Content
    )

    $documents = @($Content -split "(?m)^---\s*$" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($documents.Count -eq 0) {
        throw "$Name has no Kubernetes documents."
    }

    for ($i = 0; $i -lt $documents.Count; $i++) {
        $document = $documents[$i]
        foreach ($field in @("apiVersion:", "kind:", "metadata:")) {
            if (-not $document.Contains($field)) {
                throw "$Name document $($i + 1) is missing $field"
            }
        }
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$kubernetesDir = Join-Path $PSScriptRoot "kubernetes"
$vaultDir = Join-Path $PSScriptRoot "vault"
$githubOperationsDir = Join-Path $PSScriptRoot "github"
$githubDir = Join-Path $repoRoot ".github"
$workflowDir = Join-Path $githubDir "workflows"
$workflowPath = Join-Path $workflowDir "operations-validation.yml"

$files = @{
    RuntimeManifest = Join-Path $kubernetesDir "trading-runtime.yaml"
    InfraManifest = Join-Path $kubernetesDir "trading-infra.yaml"
    ExternalSecretsManifest = Join-Path $kubernetesDir "external-secrets.yaml"
    DeployScript = Join-Path $kubernetesDir "deploy-trading-runtime.ps1"
    VerifyScript = Join-Path $kubernetesDir "verify-trading-runtime.ps1"
    VaultScript = Join-Path $vaultDir "bootstrap-akra-vault.ps1"
    GitHubDeliveryScript = Join-Path $githubOperationsDir "verify-github-delivery.ps1"
    VaultPolicy = Join-Path $vaultDir "akra-trading-external-secrets-policy.hcl"
    VaultReadme = Join-Path $vaultDir "README.md"
    KubernetesReadme = Join-Path $kubernetesDir "README.md"
    Runbook = Join-Path $PSScriptRoot "trading-runtime-runbook.md"
    Workflow = $workflowPath
}

foreach ($path in $files.Values) {
    Assert-FileExists $path
}

Assert-PowerShellParses $files.DeployScript
Assert-PowerShellParses $files.VerifyScript
Assert-PowerShellParses $files.VaultScript
Assert-PowerShellParses $files.GitHubDeliveryScript

$vaultDryRun = & $files.VaultScript -DryRun *>&1 | Out-String
Assert-Contains "Vault dry-run" $vaultDryRun "path `"secret/data/akra/trading/kis-broker`""
Assert-Contains "Vault dry-run" $vaultDryRun "vault policy write akra-trading-external-secrets <rendered-policy-file>"
Assert-Contains "Vault dry-run" $vaultDryRun "bound_service_account_names=akra-secret-sync"

$githubDryRun = & $files.GitHubDeliveryScript -CommitSha "ae80c31584b78eeb7361184f84dc240c2bbcba81" -DryRun *>&1 | Out-String
Assert-Contains "GitHub delivery dry-run" $githubDryRun "Expected workflow: Container Images"
Assert-Contains "GitHub delivery dry-run" $githubDryRun "Expected workflow: Operations Validation"
Assert-Contains "GitHub delivery dry-run" $githubDryRun "ghcr.io/professional-level/sketch-stock-purchase-service:ae80c31584b78eeb7361184f84dc240c2bbcba81"
Assert-Contains "GitHub delivery dry-run" $githubDryRun "Registry check mode: Auto"

$runtimeVerifyDryRun = & $files.VerifyScript -ImageTag "ae80c31584b78eeb7361184f84dc240c2bbcba81" -DryRun *>&1 | Out-String
Assert-Contains "runtime verify dry-run" $runtimeVerifyDryRun "Would verify trading runtime in namespace akra-trading with image tag ae80c31584b78eeb7361184f84dc240c2bbcba81"
Assert-Contains "runtime verify dry-run" $runtimeVerifyDryRun "deployment/stock-purchase-service uses image tag ae80c31584b78eeb7361184f84dc240c2bbcba81"
Assert-Contains "runtime verify dry-run" $runtimeVerifyDryRun "Kafka topic exists: order-cancelled"

$deployPlan = & $files.DeployScript `
    -ImageTag "ae80c31584b78eeb7361184f84dc240c2bbcba81" `
    -InfraManifestPath $files.InfraManifest `
    -SecretManifestPath $files.ExternalSecretsManifest `
    -PlanOnly `
    -AllowTemplatePlaceholders *>&1 | Out-String
Assert-Contains "deploy plan" $deployPlan "Would deploy trading runtime with image tag ae80c31584b78eeb7361184f84dc240c2bbcba81"
Assert-Contains "deploy plan" $deployPlan "Would refresh SQL migration ConfigMap from:"
Assert-Contains "deploy plan" $deployPlan "Would restart deployment: akra-trading/stock-purchase-service"
Assert-Contains "deploy plan" $deployPlan "Plan only complete. Skipping kubectl commands."

$runtime = Get-Content -LiteralPath $files.RuntimeManifest -Raw
$infra = Get-Content -LiteralPath $files.InfraManifest -Raw
$externalSecrets = Get-Content -LiteralPath $files.ExternalSecretsManifest -Raw
$deployScript = Get-Content -LiteralPath $files.DeployScript -Raw
$verifyScript = Get-Content -LiteralPath $files.VerifyScript -Raw
$vaultPolicy = Get-Content -LiteralPath $files.VaultPolicy -Raw
$workflow = Get-Content -LiteralPath $files.Workflow -Raw

$renderedRuntime = Replace-All $runtime @{
    "REPLACE_WITH_REAL_APP_KEY" = "real-app-key"
    "REPLACE_WITH_REAL_APP_SECRET" = "real-app-secret"
    "REPLACE_WITH_MOCK_APP_KEY" = "mock-app-key"
    "REPLACE_WITH_MOCK_APP_SECRET" = "mock-app-secret"
    "REPLACE_WITH_REAL_ACCOUNT" = "00000000"
    "REPLACE_WITH_REAL_ACCOUNT_TAIL" = "01"
    "REPLACE_WITH_DATABASE_PASSWORD" = "database-password"
    "REPLACE_IMAGE_TAG" = "0123456789abcdef"
}

$renderedInfra = Replace-All $infra @{
    "REPLACE_MYSQL_ROOT_PASSWORD" = "mysql-root-password"
    "REPLACE_MYSQL_APP_PASSWORD" = "database-password"
    "REPLACE_TEMPORAL_POSTGRES_PASSWORD" = "temporal-postgres-password"
    "REPLACE_CONFLUENT_PLATFORM_TAG" = "7.8.0"
}

$renderedExternalSecrets = Replace-All $externalSecrets @{
    "REPLACE_VAULT_SERVER_URL" = "https://vault.example.invalid"
    "REPLACE_VAULT_KV_MOUNT" = "secret"
    "REPLACE_VAULT_KUBERNETES_AUTH_MOUNT" = "kubernetes"
    "REPLACE_VAULT_KUBERNETES_ROLE" = "akra-trading-external-secrets"
    "REPLACE_VAULT_KIS_SECRET_PATH" = "akra/trading/kis-broker"
    "REPLACE_VAULT_DATABASE_SECRET_PATH" = "akra/trading/database"
}

Assert-NoReplacePlaceholders "rendered trading-runtime.yaml" $renderedRuntime
Assert-NoReplacePlaceholders "rendered trading-infra.yaml" $renderedInfra
Assert-NoReplacePlaceholders "rendered external-secrets.yaml" $renderedExternalSecrets

Assert-KubernetesDocumentShape "trading-runtime.yaml" $renderedRuntime
Assert-KubernetesDocumentShape "trading-infra.yaml" $renderedInfra
Assert-KubernetesDocumentShape "external-secrets.yaml" $renderedExternalSecrets

foreach ($image in @(
    "ghcr.io/professional-level/sketch-kis-wrapper:REPLACE_IMAGE_TAG",
    "ghcr.io/professional-level/sketch-stock-search-service:REPLACE_IMAGE_TAG",
    "ghcr.io/professional-level/sketch-strategy-execution-service:REPLACE_IMAGE_TAG",
    "ghcr.io/professional-level/sketch-stock-purchase-service:REPLACE_IMAGE_TAG"
)) {
    Assert-Contains "trading-runtime.yaml" $runtime $image
}

foreach ($topic in @(
    "strategy-saved",
    "strategy-execution-start-requested",
    "order-intent-created",
    "order-submitted",
    "order-filled",
    "order-partially-filled",
    "order-rejected",
    "order-cancelled"
)) {
    Assert-Contains "trading-infra.yaml" $infra $topic
}

foreach ($expected in @(
    "KAFKA_AUTO_CREATE_TOPICS_ENABLE",
    "akra-kafka-topic-bootstrap",
    "temporal-frontend.trading-infra.svc.cluster.local"
)) {
    Assert-Contains "trading-infra.yaml" $infra $expected
}

foreach ($expected in @(
    "mysql.trading-infra.svc.cluster.local",
    "kafka-0.kafka.trading-infra.svc.cluster.local",
    "temporal-frontend.trading-infra.svc.cluster.local",
    "MIGRATION_MANIFEST.md",
    "grep -E '^[0-9]{8}_.+\.sql$'",
    "migration listed in manifest is missing"
)) {
    Assert-Contains "trading-runtime.yaml" $runtime $expected
}

foreach ($expected in @(
    "kis-broker-secrets",
    "trading-database-secrets",
    "akra-vault-secret-store",
    "akra-secret-sync"
)) {
    Assert-Contains "external-secrets.yaml" $externalSecrets $expected
}

foreach ($expected in @(
    "InfraManifestPath",
    "SecretManifestPath",
    "akra-sql-migrations",
    "akra-trading-schema-migration"
)) {
    Assert-Contains "deploy-trading-runtime.ps1" $deployScript $expected
}

foreach ($expected in @(
    "Assert-DeploymentImageTag",
    "akra-trading-schema-migration",
    "strategy-execution-start-requested",
    "kafka-topics",
    "Trading runtime Kubernetes verification passed."
)) {
    Assert-Contains "verify-trading-runtime.ps1" $verifyScript $expected
}

foreach ($expected in @(
    "REPLACE_VAULT_KV_MOUNT/data/REPLACE_VAULT_KIS_SECRET_PATH",
    "REPLACE_VAULT_KV_MOUNT/data/REPLACE_VAULT_DATABASE_SECRET_PATH",
    "capabilities = [`"read`"]"
)) {
    Assert-Contains "Vault policy template" $vaultPolicy $expected
}

Assert-Contains "operations-validation workflow" $workflow "validate-operations-artifacts.ps1"
Assert-Contains "operations-validation workflow" $workflow "pwsh"

Write-Host "Operations artifacts validation passed."
