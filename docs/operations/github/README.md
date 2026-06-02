# GitHub Delivery Verification

Use `verify-github-delivery.ps1` after pushing a deployment commit. It checks
that the expected GitHub Actions workflows succeeded for the exact commit SHA
and that GHCR exposes the immutable image tags used by the Kubernetes rollout.
Registry verification defaults to `Auto`: it uses Docker when Docker is on
`PATH`, otherwise it uses the GHCR Registry HTTP API.

Dry-run:

```powershell
.\verify-github-delivery.ps1 `
  -CommitSha ae80c31584b78eeb7361184f84dc240c2bbcba81 `
  -DryRun
```

Verify Actions and GHCR:

```powershell
$env:GHCR_USERNAME='REDACTED_GITHUB_LOGIN'
$env:GHCR_TOKEN='REDACTED_TOKEN_WITH_PACKAGES_READ'
.\verify-github-delivery.ps1 `
  -RepoFullName professional-level/_sketch `
  -CommitSha <git-sha>
```

Force Docker-based registry verification:

```powershell
.\verify-github-delivery.ps1 `
  -RepoFullName professional-level/_sketch `
  -CommitSha <git-sha> `
  -RegistryCheckMode Docker
```

Force Dockerless GHCR HTTP registry verification:

```powershell
$env:GHCR_USERNAME='REDACTED_GITHUB_LOGIN'
$env:GHCR_TOKEN='REDACTED_TOKEN_WITH_PACKAGES_READ'
.\verify-github-delivery.ps1 `
  -RepoFullName professional-level/_sketch `
  -CommitSha <git-sha> `
  -RegistryCheckMode Http
```

If the environment cannot access GHCR but still needs to verify workflow status:

```powershell
.\verify-github-delivery.ps1 `
  -RepoFullName professional-level/_sketch `
  -CommitSha <git-sha> `
  -SkipRegistryCheck
```

Expected workflow names:

```text
Container Images
Operations Validation
```

Expected GHCR image tags:

```text
ghcr.io/professional-level/sketch-kis-wrapper:<git-sha>
ghcr.io/professional-level/sketch-stock-search-service:<git-sha>
ghcr.io/professional-level/sketch-strategy-execution-service:<git-sha>
ghcr.io/professional-level/sketch-stock-purchase-service:<git-sha>
```

This script does not print secret values. If `GITHUB_TOKEN` is not set, it uses
unauthenticated GitHub API requests, which may be rate-limited or unable to see
private workflow/package state.

For GHCR HTTP registry verification, set either `GHCR_USERNAME` and
`GHCR_TOKEN`, or `GITHUB_ACTOR` and `GITHUB_TOKEN`. The token needs package read
permission for private packages. Public packages may be readable anonymously.
