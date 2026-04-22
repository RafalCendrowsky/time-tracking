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

> **Note:** Vault runs in **standalone mode** with a persistent file-storage backend (`/vault/data`). After the very
> first install — or after a pod restart — Vault will be **sealed** and must be initialised / unsealed manually (see
> below).

## Vault initialisation (first-time only)

```bash
# 1. Initialise Vault — generates unseal keys and the root token (save these securely!)
kubectl exec -n time-tracking time-tracking-vault-0 -- vault operator init

# 2. Unseal with 3 of the 5 keys printed above (run three times with different keys)
kubectl exec -n time-tracking time-tracking-vault-0 -- vault operator unseal <Unseal Key 1>
kubectl exec -n time-tracking time-tracking-vault-0 -- vault operator unseal <Unseal Key 2>
kubectl exec -n time-tracking time-tracking-vault-0 -- vault operator unseal <Unseal Key 3>

# 3. Log in with the root token to create secrets expected by the services
kubectl exec -n time-tracking time-tracking-vault-0 -- \
  vault login <Initial Root Token>

kubectl exec -n time-tracking time-tracking-vault-0 -- \
  vault kv put secret/auth-service <key>=<value> ...

kubectl exec -n time-tracking time-tracking-vault-0 -- \
  vault kv put secret/project-service <key>=<value> ...
```

After every **pod restart** Vault will be sealed again — repeat the `vault operator unseal` step (the data is preserved
on the PVC).

Update the `vaultToken` secret values in your `my-secrets.yaml` with the root token (or a scoped token) so the services
can authenticate.
