#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="time-tracking-kind"
NAMESPACE="time-tracking"
RELEASE_NAME="time-tracking"
REGISTRY=""
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_PATH="${REPO_ROOT}/helm/time-tracking"
VALUES_FILE="${CHART_PATH}/values.yaml"
SHARED_CA_CHART_PATH="${REPO_ROOT}/helm/shared-ca"
SHARED_CA_VALUES_FILE="${SHARED_CA_CHART_PATH}/values.yaml"
KIND_CONFIG_FILE="${SCRIPT_DIR}/kind-config.yaml"
VAULT_CHART_PATH="${REPO_ROOT}/helm/vault"
VAULT_VALUES_FILE="${VAULT_CHART_PATH}/values.yaml"
VAULT_INIT_KEYS_FILE="${SCRIPT_DIR}/vault-init-keys.json"
BUILD_IMAGES="false"

usage() {
  cat <<'EOF'
Usage: bootstrap-kind.sh [options]

Options:
  --cluster-name NAME   Kind cluster name (default: time-tracking-kind)
  --namespace NAME      Kubernetes namespace (default: time-tracking)
  --release-name NAME   Helm release name (default: time-tracking)
  --registry NAME       Image registry prefix (default: empty)
  --chart-path PATH     Helm chart path (default: helm/time-tracking)
  --values-file PATH    Helm values file (default: helm/time-tracking/values.yaml)
  --build-images        Force rebuild auth/project images before loading into kind
  -h, --help            Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --cluster-name)
      CLUSTER_NAME="$2"
      shift 2
      ;;
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --release-name)
      RELEASE_NAME="$2"
      shift 2
      ;;
    --registry)
      REGISTRY="$2"
      shift 2
      ;;
    --chart-path)
      CHART_PATH="$2"
      shift 2
      ;;
    --values-file)
      VALUES_FILE="$2"
      shift 2
      ;;
    --rebuild-images)
      BUILD_IMAGES="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' was not found on PATH." >&2
    exit 1
  fi
}

run_checked() {
  local description="$1"
  shift
  if ! "$@"; then
    echo "$description failed." >&2
    exit 1
  fi
}

image_exists() {
  docker image inspect "$1" >/dev/null 2>&1 || return 1
}

ensure_image() {
  local image="$1"
  local context_path="$2"

  if [[ "$BUILD_IMAGES" == "true" ]] || ! image_exists "$image"; then
    echo "Building $image from $context_path"
    run_checked "docker build for $image" docker build -t "$image" "$context_path"
  else
    echo "Reusing existing local image $image"
  fi

  local archive_path
  archive_path="$(mktemp -t "${CLUSTER_NAME}-$(basename "$image").XXXXXX.tar")"
  trap 'rm -f "$archive_path"' RETURN

  echo "Saving $image to $archive_path"
  run_checked "docker save for $image" docker save -o "$archive_path" "$image"

  echo "Loading $image archive into kind cluster $CLUSTER_NAME"
  run_checked "kind load image-archive $image" kind load image-archive "$archive_path" --name "$CLUSTER_NAME"
}

image_name() {
  local repository="$1"

  if [[ -z "$REGISTRY" ]]; then
    printf '%s\n' "$repository"
  else
    printf '%s/%s\n' "$REGISTRY" "$repository"
  fi
}

require_cmd kind
require_cmd kubectl
require_cmd helm
require_cmd docker

if ! kind get clusters | grep -qx "$CLUSTER_NAME"; then
  echo "Creating kind cluster '$CLUSTER_NAME'"
  run_checked "kind create cluster" kind create cluster --name "$CLUSTER_NAME" --config "$KIND_CONFIG_FILE"
else
  echo "Kind cluster '$CLUSTER_NAME' already exists"
fi

echo "Adding Helm repositories"
run_checked "helm repo add jetstack" helm repo add jetstack https://charts.jetstack.io --force-update
run_checked "helm repo add ingress-nginx" helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx --force-update
run_checked "helm repo add cnpg" helm repo add cnpg https://cloudnative-pg.github.io/charts --force-update
run_checked "helm repo add mongodb" helm repo add mongodb https://mongodb.github.io/helm-charts --force-update
run_checked "helm repo add hashicorp" helm repo add hashicorp https://helm.releases.hashicorp.com --force-update
run_checked "helm repo update" helm repo update

echo "Installing cert-manager"
run_checked "helm upgrade --install cert-manager" helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set crds.enabled=true \
  --history-max 1 \
  --wait \
  --timeout 10m

echo "Waiting for cert-manager deployments to become ready"
run_checked "kubectl rollout status cert-manager" kubectl rollout status deployment/cert-manager --namespace cert-manager --timeout=10m
run_checked "kubectl rollout status cert-manager-cainjector" kubectl rollout status deployment/cert-manager-cainjector --namespace cert-manager --timeout=10m
run_checked "kubectl rollout status cert-manager-webhook" kubectl rollout status deployment/cert-manager-webhook --namespace cert-manager --timeout=10m

run_checked "helm upgrade --install ingress-nginx" helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --set controller.kind=DaemonSet \
  --set-string controller.nodeSelector.ingress-ready=true \
  --set controller.hostPort.enabled=true \
  --set controller.hostPort.ports.http=80 \
  --set controller.hostPort.ports.https=443 \
  --set controller.service.type=ClusterIP \
  --history-max 1 \
  --wait \
  --timeout 10m

if [[ ! -d "$CHART_PATH" ]]; then
  echo "Helm chart path '$CHART_PATH' does not exist." >&2
  exit 1
fi
if [[ ! -f "$VALUES_FILE" ]]; then
  echo "Values file '$VALUES_FILE' does not exist." >&2
  exit 1
fi
if [[ ! -d "$SHARED_CA_CHART_PATH" ]]; then
  echo "Shared CA chart path '$SHARED_CA_CHART_PATH' does not exist." >&2
  exit 1
fi
if [[ ! -f "$SHARED_CA_VALUES_FILE" ]]; then
  echo "Shared CA values file '$SHARED_CA_VALUES_FILE' does not exist." >&2
  exit 1
fi
if [[ ! -f "$KIND_CONFIG_FILE" ]]; then
  echo "Kind config file '$KIND_CONFIG_FILE' does not exist." >&2
  exit 1
fi
if [[ ! -d "$VAULT_CHART_PATH" ]]; then
  echo "Vault chart path '$VAULT_CHART_PATH' does not exist." >&2
  exit 1
fi
if [[ ! -f "$VAULT_VALUES_FILE" ]]; then
  echo "Vault values file '$VAULT_VALUES_FILE' does not exist." >&2
  exit 1
fi

echo "Updating chart dependencies"
run_checked "helm dependency update shared-ca" helm dependency update "$SHARED_CA_CHART_PATH"
run_checked "helm dependency update" helm dependency update "$CHART_PATH"
run_checked "helm dependency update vault" helm dependency update "$VAULT_CHART_PATH"

echo "Installing CloudNativePG operator"
run_checked "helm upgrade --install cnpg-operator" helm upgrade --install cnpg-operator cnpg/cloudnative-pg \
  --namespace cnpg-system \
  --create-namespace \
  --set installCRDs=true \
  --history-max 1 \
  --wait \
  --timeout 5m

echo "Installing MongoDB Community operator"
run_checked "helm upgrade --install community-operator" helm upgrade --install mongodb-operator mongodb/community-operator \
  --namespace mongodb \
  --create-namespace \
  --set installCRDs=true \
  --set operator.watchNamespace="*" \
  --history-max 1 \
  --wait \
  --timeout 5m

wait_for_crd() {
  local crd="$1"
  local timeout_seconds=${2:-120}
  local end_time=$(( $(date +%s) + timeout_seconds ))
  while [ $(date +%s) -lt $end_time ]; do
    if kubectl get crd "$crd" >/dev/null 2>&1; then
      echo "CRD $crd is present"
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for CRD $crd" >&2
  return 1
}


echo "Waiting for operator CRDs to be established"
if ! wait_for_crd "clusters.postgresql.cnpg.io" 120; then
  echo "clusters.postgresql.cnpg.io not found, attempting alternative name" >&2
  wait_for_crd "cluster.postgresql.cnpg.io" 60 || echo "CNPG CRD not detected after retries" >&2
fi

if ! wait_for_crd "mongodbcommunity.mongodbcommunity.mongodb.com" 120; then
  echo "mongodbcommunity.mongodbcommunity.mongodb.com not found after timeout" >&2
fi

echo "Creating Vault namespace 'vault' if needed"
if ! kubectl get namespace vault >/dev/null 2>&1; then
  run_checked "kubectl create namespace vault" kubectl create namespace vault
fi

echo "Deploying shared CA release 'shared-ca' into namespace 'cert-manager'"
run_checked "helm upgrade --install shared-ca" helm upgrade --install shared-ca "$SHARED_CA_CHART_PATH" \
  --namespace cert-manager \
  --create-namespace \
  --values "$SHARED_CA_VALUES_FILE" \
  --history-max 3 \
  --wait \
  --timeout 10m

echo "Deploying Vault release '$RELEASE_NAME' into namespace 'vault'"
run_checked "helm upgrade --install vault" helm upgrade --install "$RELEASE_NAME" "$VAULT_CHART_PATH" \
  --namespace vault \
  --create-namespace \
  --values "$VAULT_VALUES_FILE" \
  --skip-crds \
  --history-max 3 \
  --wait \
  --timeout 15m

ensure_image "$(image_name 'time-tracking/auth-service:latest')" "${REPO_ROOT}/services/auth-service"
ensure_image "$(image_name 'time-tracking/project-service:latest')" "${REPO_ROOT}/services/project-service"

echo "Creating namespace '$NAMESPACE' if needed"
if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
  run_checked "kubectl create namespace" kubectl create namespace "$NAMESPACE"
fi

SECRETS_FILE="$(dirname "$VALUES_FILE")/secrets.yaml"

echo "Deploying Helm release '$RELEASE_NAME'"
helm_args=(
  upgrade --install "$RELEASE_NAME" "$CHART_PATH"
  --namespace "$NAMESPACE"
  --create-namespace
  --values "$VALUES_FILE"
  --skip-crds
  --history-max 3
)
if [[ -f "$SECRETS_FILE" ]]; then
  echo "Applying secrets override from $SECRETS_FILE"
  helm_args+=(--values "$SECRETS_FILE")
fi
run_checked "helm upgrade --install $RELEASE_NAME" helm "${helm_args[@]}"

echo
echo "Bootstrap complete."
echo "Useful follow-up commands:"
echo "  kubectl -n $NAMESPACE get pods"
echo "  kubectl -n $NAMESPACE port-forward svc/$RELEASE_NAME-auth-service 8443:443"
echo "  kubectl -n $NAMESPACE port-forward svc/$RELEASE_NAME-project-service 8444:443"

