# Akra Trading Vault Bootstrap

This directory contains Vault-side templates for the Kubernetes
`external-secrets.yaml` manifest. It prepares the least-privilege read policy and
Kubernetes auth role needed by External Secrets Operator. It does not store real
KIS credentials or database passwords in Git.

## Expected Secret Shape

Default KV v2 mount and paths:

```text
secret/akra/trading/kis-broker
secret/akra/trading/database
```

The KIS secret must contain these fields:

```text
app-key
app-secret
mock-app-key
mock-app-secret
account
account-tail
```

The database secret must contain these fields:

```text
host
port
database
username
password
```

For the checked-in infra bootstrap template, `password` must match
`trading-infra-secrets.mysql-app-password`.

## Bootstrap Policy And Role

Prerequisites:

- Vault CLI is authenticated as an operator that can write policies and
  Kubernetes auth roles.
- The Vault KV v2 mount exists.
- Kubernetes auth is enabled and configured for the target cluster.
- External Secrets Operator is installed in the cluster.

Dry-run:

```powershell
.\bootstrap-akra-vault.ps1 -DryRun
```

Apply:

```powershell
.\bootstrap-akra-vault.ps1 `
  -KvMount secret `
  -KisSecretPath akra/trading/kis-broker `
  -DatabaseSecretPath akra/trading/database `
  -KubernetesAuthMount kubernetes `
  -KubernetesRole akra-trading-external-secrets `
  -KubernetesNamespace akra-trading `
  -KubernetesServiceAccount akra-secret-sync
```

After running it, prepare `docs/operations/kubernetes/external-secrets.yaml`
with:

```text
REPLACE_VAULT_KV_MOUNT=secret
REPLACE_VAULT_KIS_SECRET_PATH=akra/trading/kis-broker
REPLACE_VAULT_DATABASE_SECRET_PATH=akra/trading/database
REPLACE_VAULT_KUBERNETES_AUTH_MOUNT=kubernetes
REPLACE_VAULT_KUBERNETES_ROLE=akra-trading-external-secrets
```

## Secret Write Example

Write real values directly into Vault from an operator shell. Do not put these
commands with real values into Git history, shell history, or CI logs.

```powershell
vault kv put secret/akra/trading/kis-broker `
  app-key=REDACTED `
  app-secret=REDACTED `
  mock-app-key=REDACTED `
  mock-app-secret=REDACTED `
  account=REDACTED `
  account-tail=REDACTED

vault kv put secret/akra/trading/database `
  host=mysql.trading-infra.svc.cluster.local `
  port=3306 `
  database=akra_trading `
  username=akra_trading `
  password=REDACTED
```

## Verification

```powershell
vault policy read akra-trading-external-secrets
vault read auth/kubernetes/role/akra-trading-external-secrets
kubectl -n akra-trading get secretstore akra-vault-secret-store
kubectl -n akra-trading get externalsecret kis-broker-secrets trading-database-secrets
kubectl -n akra-trading get secret kis-broker-secrets trading-database-secrets
```

The Kubernetes Secret names are intentionally the same names mounted by
`trading-runtime.yaml`.
