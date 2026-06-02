[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$")]
    [string] $ImageTag,

    [string] $Namespace = "akra-trading",

    [string] $InfraNamespace = "trading-infra",

    [int] $TimeoutSeconds = 300,

    [switch] $SkipInfra,

    [switch] $SkipExternalSecrets,

    [switch] $SkipKafkaTopics
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

function Require-Command {
    param([string] $Name)

    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found on PATH."
    }
}

function Invoke-KubectlCapture {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)

    Write-Host ("kubectl {0}" -f ($Arguments -join " "))
    $output = & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl failed with exit code $LASTEXITCODE"
    }
    return $output
}

function Invoke-Kubectl {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)

    Invoke-KubectlCapture @Arguments | Out-Null
}

function Assert-DeploymentImageTag {
    param(
        [string] $Deployment,
        [string] $ExpectedTag
    )

    $images = @(Invoke-KubectlCapture "-n" $Namespace "get" "deployment/$Deployment" "-o" "jsonpath={.spec.template.spec.containers[*].image}")
    $joined = $images -join " "
    if (-not $joined.Contains(":$ExpectedTag")) {
        throw "deployment/$Deployment does not use image tag $ExpectedTag. Images: $joined"
    }
}

function Assert-KubernetesObjectExists {
    param(
        [string] $Kind,
        [string] $Name,
        [string] $ObjectNamespace
    )

    Invoke-Kubectl "-n" $ObjectNamespace "get" "$Kind/$Name"
}

Require-Command "kubectl"

$applicationDeployments = @(
    "kis-wrapper",
    "stock-search-service",
    "strategy-execution-service",
    "stock-purchase-service"
)

foreach ($secret in @("kis-broker-secrets", "trading-database-secrets")) {
    Assert-KubernetesObjectExists "secret" $secret $Namespace
}

if (-not $SkipExternalSecrets) {
    Assert-KubernetesObjectExists "secretstore" "akra-vault-secret-store" $Namespace
    foreach ($externalSecret in @("kis-broker-secrets", "trading-database-secrets")) {
        Assert-KubernetesObjectExists "externalsecret" $externalSecret $Namespace
    }
}

Assert-KubernetesObjectExists "configmap" "akra-trading-runtime-config" $Namespace
Assert-KubernetesObjectExists "configmap" "akra-sql-migrations" $Namespace

Invoke-Kubectl "-n" $Namespace "wait" "--for=condition=complete" "job/akra-trading-schema-migration" "--timeout=$($TimeoutSeconds)s"

foreach ($deployment in $applicationDeployments) {
    Assert-DeploymentImageTag $deployment $ImageTag
    Invoke-Kubectl "-n" $Namespace "rollout" "status" "deployment/$deployment" "--timeout=$($TimeoutSeconds)s"
}

if (-not $SkipInfra) {
    foreach ($statefulSet in @("mysql", "zookeeper", "kafka", "temporal-postgresql")) {
        Invoke-Kubectl "-n" $InfraNamespace "rollout" "status" "statefulset/$statefulSet" "--timeout=$($TimeoutSeconds)s"
    }

    foreach ($deployment in @("temporal", "temporal-ui")) {
        Invoke-Kubectl "-n" $InfraNamespace "rollout" "status" "deployment/$deployment" "--timeout=$($TimeoutSeconds)s"
    }

    foreach ($service in @("mysql", "kafka", "temporal-frontend", "temporal-ui")) {
        Assert-KubernetesObjectExists "service" $service $InfraNamespace
    }

    if (-not $SkipKafkaTopics) {
        $topicList = @(Invoke-KubectlCapture "-n" $InfraNamespace "exec" "statefulset/kafka" "--" "kafka-topics" "--bootstrap-server" "kafka-0.kafka.$InfraNamespace.svc.cluster.local:9092" "--list")
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
            if ($topicList -notcontains $topic) {
                throw "Kafka topic is missing: $topic"
            }
        }
    }
}

Write-Host "Trading runtime Kubernetes verification passed."
