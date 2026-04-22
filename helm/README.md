# Helm Charts — time-tracking

## Structure

```
helm/
└── time-tracking/          ← umbrella chart
    ├── Chart.yaml          ← dependencies: mongodb, postgresql, vault, auth-service, project-service
    ├── values.yaml         ← all overrides in one place
    ├── templates/
    │   └── _helpers.tpl
    ├── auth-service/       ← first-party sub-chart
    └── project-service/    ← first-party sub-chart
```

## Prerequisites

- [Helm 3](https://helm.sh/docs/intro/install/)
- A running Kubernetes cluster (e.g. `kind`, `minikube`, Docker Desktop)
- Docker images built and available to the cluster (see below)

## Kind bootstrap scripts

For PowerShell use `scripts/bootstrap-kind.ps1`. For Bash use `scripts/bootstrap-kind.sh`.

```powershell
# PowerShell
.\scripts\bootstrap-kind.ps1
.\scripts\bootstrap-kind.ps1 -RebuildImages
```

```bash
# Bash / WSL
bash ./scripts/bootstrap-kind.sh
bash ./scripts/bootstrap-kind.sh --rebuild-images
```

The scripts use these defaults:

- kind cluster: `time-tracking-kind`
- namespace / Helm release: `time-tracking`
- chart: `helm/time-tracking`
- values file: `helm/time-tracking/values.yaml`

## Service DNS names inside the cluster

| Service         | DNS name                        | Port  |
|-----------------|---------------------------------|-------|
| MongoDB         | `time-tracking-mongodb`         | 27017 |
| PostgreSQL      | `time-tracking-postgresql`      | 5432  |
| Vault           | `time-tracking-vault`           | 8200  |
| auth-service    | `time-tracking-auth-service`    | 443   |
| project-service | `time-tracking-project-service` | 443   |
