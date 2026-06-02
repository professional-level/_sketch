[CmdletBinding()]
param(
    [string] $PolicyTemplatePath,

    [string] $PolicyName = "akra-trading-external-secrets",

    [string] $KvMount = "secret",

    [string] $KisSecretPath = "akra/trading/kis-broker",

    [string] $DatabaseSecretPath = "akra/trading/database",

    [string] $KubernetesAuthMount = "kubernetes",

    [string] $KubernetesRole = "akra-trading-external-secrets",

    [string] $KubernetesNamespace = "akra-trading",

    [string] $KubernetesServiceAccount = "akra-secret-sync",

    [string] $RoleTtl = "1h",

    [switch] $DryRun
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

function Require-Command {
    param([string] $Name)

    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found on PATH."
    }
}

function Assert-RelativeVaultPath {
    param(
        [string] $Name,
        [string] $Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name must not be blank."
    }

    if ($Value.StartsWith("/") -or $Value.EndsWith("/")) {
        throw "$Name must be a relative Vault path without leading or trailing slash: $Value"
    }
}

function Invoke-Vault {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)

    Write-Host ("vault {0}" -f ($Arguments -join " "))
    & vault @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "vault failed with exit code $LASTEXITCODE"
    }
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($scriptRoot)) {
    $scriptRoot = (Get-Location).Path
}

if ([string]::IsNullOrWhiteSpace($PolicyTemplatePath)) {
    $PolicyTemplatePath = Join-Path $scriptRoot "akra-trading-external-secrets-policy.hcl"
}

Assert-RelativeVaultPath "KvMount" $KvMount
Assert-RelativeVaultPath "KisSecretPath" $KisSecretPath
Assert-RelativeVaultPath "DatabaseSecretPath" $DatabaseSecretPath
Assert-RelativeVaultPath "KubernetesAuthMount" $KubernetesAuthMount

if (-not (Test-Path -LiteralPath $PolicyTemplatePath)) {
    throw "Policy template was not found: $PolicyTemplatePath"
}

$replacements = @{
    "REPLACE_VAULT_KV_MOUNT" = $KvMount
    "REPLACE_VAULT_KIS_SECRET_PATH" = $KisSecretPath
    "REPLACE_VAULT_DATABASE_SECRET_PATH" = $DatabaseSecretPath
}

$policy = Get-Content -LiteralPath $PolicyTemplatePath -Raw
foreach ($entry in $replacements.GetEnumerator()) {
    $policy = $policy.Replace($entry.Key, $entry.Value)
}

$tempPolicy = Join-Path ([System.IO.Path]::GetTempPath()) ("akra-vault-policy-{0}.hcl" -f [Guid]::NewGuid())

try {
    Set-Content -LiteralPath $tempPolicy -Value $policy -Encoding UTF8

    if ($DryRun) {
        Write-Host "Rendered Vault policy:"
        Get-Content -LiteralPath $tempPolicy
        Write-Host ""
        Write-Host "Dry-run Vault commands:"
        Write-Host "vault policy write $PolicyName <rendered-policy-file>"
        Write-Host "vault write auth/$KubernetesAuthMount/role/$KubernetesRole bound_service_account_names=$KubernetesServiceAccount bound_service_account_namespaces=$KubernetesNamespace policies=$PolicyName ttl=$RoleTtl"
    } else {
        Require-Command "vault"
        Invoke-Vault "policy" "write" $PolicyName $tempPolicy
        Invoke-Vault "write" "auth/$KubernetesAuthMount/role/$KubernetesRole" `
            "bound_service_account_names=$KubernetesServiceAccount" `
            "bound_service_account_namespaces=$KubernetesNamespace" `
            "policies=$PolicyName" `
            "ttl=$RoleTtl"
    }
}
finally {
    Remove-Item -LiteralPath $tempPolicy -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "Use these values when preparing docs/operations/kubernetes/external-secrets.yaml:"
Write-Host "REPLACE_VAULT_KV_MOUNT=$KvMount"
Write-Host "REPLACE_VAULT_KIS_SECRET_PATH=$KisSecretPath"
Write-Host "REPLACE_VAULT_DATABASE_SECRET_PATH=$DatabaseSecretPath"
Write-Host "REPLACE_VAULT_KUBERNETES_AUTH_MOUNT=$KubernetesAuthMount"
Write-Host "REPLACE_VAULT_KUBERNETES_ROLE=$KubernetesRole"
