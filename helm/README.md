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
│   ├── Chart.yaml          ← dependencies: auth-service, project-service
│   ├── values.yaml         ← app-level overrides in one place
│   ├── templates/
│   │   ├── _helpers.tpl
│   │   ├── database-credentials.yaml
│   │   ├── mongodb-community.yaml
│   │   ├── mongodb-serviceaccount.yml
│   │   ├── postgresql-cluster.yaml
│   │   └── tls-certificates.yaml
│   ├── auth-service/       ← first-party sub-chart
│   └── project-service/    ← first-party sub-chart
└── vault/                  ← dedicated Vault release (namespace: vault)
    ├── Chart.yaml          ← dependency: hashicorp/vault
    ├── values.yaml         ← Vault-specific overrides
    └── templates/
        └── tls-certificates.yaml
```

## Prerequisites

- [Helm 3](https://helm.sh/docs/intro/install/)
- A running Kubernetes cluster (e.g. `kind`, `minikube`, Docker Desktop)

## Scripts

### Fresh kind cluster bootstrap

Use these when you want to create a kind cluster, install everything, and build or load local images.

```powershell
# PowerShell
.\scripts\bootstrap-kind.ps1
.\scripts\bootstrap-kind.ps1 -BuildImages
```

```bash
# Bash / WSL
bash ./scripts/bootstrap-kind.sh
bash ./scripts/bootstrap-kind.sh --build-images
```

Defaults:

- kind cluster: `time-tracking-kind`
- namespace / Helm release: `time-tracking`
- Vault namespace: `vault`
- shared CA release: `shared-ca` in namespace `cert-manager`
- chart: `helm/time-tracking`
- values file: `helm/time-tracking/values.yaml`

Kind bootstrap expects host ports `80` and `443` to be free so ingress-nginx can be reached from the browser.

### Deploy the application into an existing cluster

Use these when `cert-manager`, `shared-ca`, and `vault` are already installed and you only want to deploy or refresh the
app chart.

```powershell
# PowerShell
.\scripts\deploy-app.ps1
.\scripts\deploy-app.ps1 -SkipImages
.\scripts\deploy-app.ps1 -BuildImages
```

```bash
# Bash / WSL
bash ./scripts/deploy-app.sh
bash ./scripts/deploy-app.sh --skip-images
bash ./scripts/deploy-app.sh --rebuild-images
```

`deploy-app` defaults to the `time-tracking` namespace/release and skips CRDs because the operators are expected to
exist already.

### Initialize Vault

Run this after Vault is installed so it can initialize/unseal Vault and seed the application secrets.

```powershell
# PowerShell
.\scripts\init-vault.ps1
.\scripts\init-vault.ps1 -OutputFile .\scripts\vault-init-keys.json -UnsealKeysFile .\scripts\vault-init-keys.json -ClientSecret base-client-secret -TimeoutSec 180
```

```bash
# Bash / WSL
bash ./scripts/init-vault.sh
```

`init-vault` writes the generated keys to `scripts/vault-init-keys.json` by default. The PowerShell script also supports
`-KeyShares`, `-KeyThreshold`, `-ClientSecret`, `-OutputFile`, `-UnsealKeysFile`, and `-TimeoutSec` for custom
initialization.

The Bash wrapper supports the same workflow with `--release-name`, `--namespace`, `--app-release-name`,
`--app-namespace`, `--key-shares`, `--key-threshold`, `--output-file`, `--unseal-keys-file`, `--client-secret`, and
`--timeout`.

Install order:

1. `cert-manager`
2. `ingress-nginx`
3. `shared-ca` (creates the shared CA and `ClusterIssuer`)
4. `vault`
5. `init-vault`
6. `time-tracking`

If you already have a cluster and only want to deploy the app chart, use `deploy-app` instead.

## Service DNS names inside the cluster

| Service         | Namespace       | DNS name                                      | Port  |
|-----------------|-----------------|-----------------------------------------------|-------|
| MongoDB         | `time-tracking` | `time-tracking-mongodb-svc`                   | 27017 |
| PostgreSQL      | `time-tracking` | `time-tracking-postgresql-rw`                 | 5432  |
| Vault           | `vault`         | `time-tracking-vault.vault.svc.cluster.local` | 8200  |
| auth-service    | `time-tracking` | `time-tracking-auth-service`                  | 443   |
| project-service | `time-tracking` | `time-tracking-project-service`               | 443   |
