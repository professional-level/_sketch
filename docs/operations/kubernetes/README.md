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

Images are built by `.github/workflows/container-images.yml` for:

- `ghcr.io/professional-level/sketch-kis-wrapper`
- `ghcr.io/professional-level/sketch-stock-search-service`
- `ghcr.io/professional-level/sketch-strategy-execution-service`
- `ghcr.io/professional-level/sketch-stock-purchase-service`

The workflow tags each image with the Git SHA and `latest` on push or manual
dispatch. Prefer the immutable Git SHA tag when replacing `REPLACE_IMAGE_TAG` in
the template.

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
