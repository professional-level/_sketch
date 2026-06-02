# GitHub Delivery Verification

Use `verify-github-delivery.ps1` after pushing a deployment commit. It checks
that the expected GitHub Actions workflows succeeded for the exact commit SHA
and, when Docker is available, that GHCR exposes the immutable image tags used by
the Kubernetes rollout.

Dry-run:

```powershell
.\verify-github-delivery.ps1 `
  -CommitSha ae80c31584b78eeb7361184f84dc240c2bbcba81 `
  -DryRun
```

Verify Actions and GHCR:

```powershell
$env:GITHUB_TOKEN='REDACTED_TOKEN_WITH_ACTIONS_AND_PACKAGES_READ'
.\verify-github-delivery.ps1 `
  -RepoFullName professional-level/_sketch `
  -CommitSha <git-sha>
```

If the environment cannot use Docker but still needs to verify workflow status:

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
