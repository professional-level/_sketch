# Trading Runtime Kubernetes Template

This directory contains a deployment template for running the trading sketch
with production-like profiles. It is intentionally a template, not a ready
production platform.

Before applying `trading-runtime.yaml`:

1. Replace every `REPLACE_...` value with values from the deployment secret
   manager or a one-time generated Kubernetes Secret.
2. Replace image tags with immutable image digests or release tags.
3. Replace MySQL, Kafka, and Temporal service DNS names with the target cluster
   addresses.
4. Apply the SQL migration manifest before starting or restarting the
   application deployments.
5. Keep `application-secret.properties` out of the image and out of mounted
   ConfigMaps. KIS credentials should be mounted through the `kis-broker-secrets`
   Secret and read by the wrapper through `*_FILE` environment variables.

`external-secrets.yaml` is an optional External Secrets Operator template for a
Vault-backed deployment. It syncs the checked-in Secret names expected by
`trading-runtime.yaml`:

- `kis-broker-secrets`
- `trading-database-secrets`

The template uses `external-secrets.io/v1` `SecretStore` and `ExternalSecret`
resources. Confirm the installed External Secrets Operator CRD version in the
target cluster before applying it.

`trading-infra.yaml` is an optional bootstrap template for the runtime
dependencies expected by `trading-runtime.yaml`:

- MySQL at `mysql.trading-infra.svc.cluster.local:3306`
- Kafka brokers at `kafka-0.kafka.trading-infra.svc.cluster.local:9092`,
  `kafka-1.kafka.trading-infra.svc.cluster.local:9092`, and
  `kafka-2.kafka.trading-infra.svc.cluster.local:9092`
- Temporal frontend at `temporal-frontend.trading-infra.svc.cluster.local:7233`

Use managed services or a dedicated operator when available. The checked-in
infra manifest is a bootstrap template with PVCs and a Kafka topic creation Job,
not a complete HA operations platform.

When using this bootstrap template, the prepared
`trading-infra-secrets.mysql-app-password` value must match
`trading-database-secrets.password` in the application namespace. Otherwise the
SQL migration Job and application pods will fail database authentication.

Images are built by `.github/workflows/container-images.yml` for:

- `ghcr.io/professional-level/sketch-kis-wrapper`
- `ghcr.io/professional-level/sketch-stock-search-service`
- `ghcr.io/professional-level/sketch-strategy-execution-service`
- `ghcr.io/professional-level/sketch-stock-purchase-service`

The workflow tags each image with the Git SHA and `latest` on push or manual
dispatch. Prefer the immutable Git SHA tag when replacing `REPLACE_IMAGE_TAG` in
the template.

To validate the checked-in template without applying it:

```powershell
.\deploy-trading-runtime.ps1 `
  -ImageTag 0123456789abcdef `
  -InfraManifestPath .\trading-infra.yaml `
  -SecretManifestPath .\external-secrets.yaml `
  -DryRun `
  -AllowTemplatePlaceholders
```

For an actual rollout, first create a prepared manifest copy where every
`REPLACE_...` value has been replaced by the deployment secret manager or
cluster-specific values. If using the checked-in infra and External Secrets
Operator templates, prepare matching `trading-infra.prepared.yaml` and
`external-secrets.prepared.yaml` files, then run:

```powershell
.\deploy-trading-runtime.ps1 `
  -ImageTag <git-sha> `
  -InfraManifestPath .\trading-infra.prepared.yaml `
  -SecretManifestPath .\external-secrets.prepared.yaml `
  -ManifestPath .\trading-runtime.prepared.yaml
```

The helper refuses unresolved placeholders during real rollout. It renders the
image tag, applies the optional infra manifest and waits for its StatefulSets,
topic bootstrap Job, and Deployments, applies the optional secret manifest,
applies the runtime manifest, updates the SQL migration ConfigMap, recreates and
unsuspends the migration Job, waits for it to complete, then restarts and waits
for the service Deployments.

For a local dry build, stage the boot jar and build with the matching
Dockerfile:

```powershell
.\gradlew.bat --no-daemon :stock-purchase-service:bootJar
New-Item -ItemType Directory -Force .docker\stock-purchase-service | Out-Null
$jar = Get-ChildItem stock-purchase-service\build\libs\*.jar |
  Where-Object { $_.Name -notlike '*-plain.jar' } |
  Select-Object -First 1
Copy-Item $jar.FullName .docker\stock-purchase-service\app.jar
docker build `
  -f stock-purchase-service\Dockerfile `
  --build-arg JAR_FILE=.docker/stock-purchase-service/app.jar `
  --build-arg APP_PORT=8083 `
  -t ghcr.io/professional-level/sketch-stock-purchase-service:local .
```

Create the SQL migration ConfigMap from the checked-in migration directory
when running from this directory:

```powershell
kubectl -n akra-trading create configmap akra-sql-migrations `
  --from-file=..\\sql
```

The migration Job is checked in with `spec.suspend: true`. After verifying the
generated `akra-sql-migrations` ConfigMap and database Secret:

```powershell
kubectl -n akra-trading patch job akra-trading-schema-migration `
  --type merge `
  -p '{\"spec\":{\"suspend\":false}}'
```

After the Job completes successfully, deploy or restart the application
Deployments. Startup safety validators should reject local Temporal/broker
endpoints, automatic Hibernate DDL, mock/live mismatches, disabled critical risk
guards, local `application-secret.properties` property sources, and local-file
KIS token persistence under production-like profiles.
