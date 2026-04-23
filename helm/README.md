# Helm Charts — time-tracking

## Structure

```
helm/
├── shared-ca/               ← dedicated shared CA / ClusterIssuer chart
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       └── shared-ca.yaml
├── time-tracking/          ← umbrella app chart
│   ├── Chart.yaml          ← dependencies: mongodb, postgresql, auth-service, project-service
│   ├── values.yaml         ← app-level overrides in one place
│   ├── templates/
│   │   └── _helpers.tpl
│   ├── auth-service/       ← first-party sub-chart
│   └── project-service/    ← first-party sub-chart
└── vault/                  ← dedicated Vault release (namespace: vault)
    ├── Chart.yaml          ← dependency: hashicorp/vault
    ├── values.yaml         ← Vault-specific overrides
    ├── templates/
    │   ├── tls-certificates.yaml
    │   └── tls-issuers.yaml
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
- Vault namespace: `vault`
- shared CA release: `shared-ca` in namespace `cert-manager`
- chart: `helm/time-tracking`
- values file: `helm/time-tracking/values.yaml`

Install order:

1. `cert-manager`
2. `shared-ca` (creates the shared CA and `ClusterIssuer`)
3. `vault`
4. `time-tracking`

## Service DNS names inside the cluster

| Service         | DNS name                        | Port  |
|-----------------|---------------------------------|-------|
| MongoDB         | `time-tracking-mongodb`         | 27017 |
| PostgreSQL      | `time-tracking-postgresql`      | 5432  |
| Vault           | `time-tracking-vault.vault`     | 8200  |
| auth-service    | `time-tracking-auth-service`    | 443   |
| project-service | `time-tracking-project-service` | 443   |
