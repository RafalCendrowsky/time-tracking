#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="time-tracking-kind"
NAMESPACE="time-tracking"
RELEASE_NAME="time-tracking"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART_PATH="${REPO_ROOT}/helm/time-tracking"
VALUES_FILE="${CHART_PATH}/values.yaml"
REBUILD_IMAGES="false"

usage() {
  cat <<'EOF'
Usage: bootstrap-kind.sh [options]

Options:
  --cluster-name NAME   Kind cluster name (default: time-tracking-kind)
  --namespace NAME      Kubernetes namespace (default: time-tracking)
  --release-name NAME   Helm release name (default: time-tracking)
  --chart-path PATH     Helm chart path (default: helm/time-tracking)
  --values-file PATH    Helm values file (default: helm/time-tracking/values.yaml)
  --rebuild-images      Force rebuild auth/project images before loading into kind
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
    --chart-path)
      CHART_PATH="$2"
      shift 2
      ;;
    --values-file)
      VALUES_FILE="$2"
      shift 2
      ;;
    --rebuild-images)
      REBUILD_IMAGES="true"
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
  docker image inspect "$1" >/dev/null 2>&1
}

ensure_image() {
  local image="$1"
  local context_path="$2"

  if [[ "$REBUILD_IMAGES" == "true" ]] || ! image_exists "$image"; then
    echo "Building $image from $context_path"
    run_checked "docker build for $image" docker build -t "$image" "$context_path"
  else
    echo "Reusing existing local image $image"
  fi

  echo "Loading $image into kind cluster $CLUSTER_NAME"
  run_checked "kind load docker-image $image" kind load docker-image "$image" --name "$CLUSTER_NAME"
}

require_cmd kind
require_cmd kubectl
require_cmd helm
require_cmd docker

if ! kind get clusters | grep -qx "$CLUSTER_NAME"; then
  echo "Creating kind cluster '$CLUSTER_NAME'"
  run_checked "kind create cluster" kind create cluster --name "$CLUSTER_NAME"
else
  echo "Kind cluster '$CLUSTER_NAME' already exists"
fi

echo "Adding Helm repositories"
run_checked "helm repo add jetstack" helm repo add jetstack https://charts.jetstack.io --force-update
run_checked "helm repo add bitnami" helm repo add bitnami https://charts.bitnami.com/bitnami --force-update
run_checked "helm repo add hashicorp" helm repo add hashicorp https://helm.releases.hashicorp.com --force-update
run_checked "helm repo update" helm repo update

echo "Installing cert-manager"
run_checked "helm upgrade --install cert-manager" helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set crds.enabled=true \
  --wait \
  --timeout 10m

echo "Waiting for cert-manager deployments to become ready"
run_checked "kubectl rollout status cert-manager" kubectl rollout status deployment/cert-manager --namespace cert-manager --timeout=10m
run_checked "kubectl rollout status cert-manager-cainjector" kubectl rollout status deployment/cert-manager-cainjector --namespace cert-manager --timeout=10m
run_checked "kubectl rollout status cert-manager-webhook" kubectl rollout status deployment/cert-manager-webhook --namespace cert-manager --timeout=10m

if [[ ! -d "$CHART_PATH" ]]; then
  echo "Helm chart path '$CHART_PATH' does not exist." >&2
  exit 1
fi
if [[ ! -f "$VALUES_FILE" ]]; then
  echo "Values file '$VALUES_FILE' does not exist." >&2
  exit 1
fi

echo "Updating chart dependencies"
run_checked "helm dependency update" helm dependency update "$CHART_PATH"

ensure_image "time-tracking/auth-service:latest" "${REPO_ROOT}/services/auth-service"
ensure_image "time-tracking/project-service:latest" "${REPO_ROOT}/services/project-service"

echo "Creating namespace '$NAMESPACE' if needed"
if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
  run_checked "kubectl create namespace" kubectl create namespace "$NAMESPACE"
fi

echo "Deploying Helm release '$RELEASE_NAME'"
run_checked "helm upgrade --install $RELEASE_NAME" helm upgrade --install "$RELEASE_NAME" "$CHART_PATH" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --values "$VALUES_FILE" \
  --wait \
  --timeout 15m

echo
echo "Bootstrap complete."
echo "Useful follow-up commands:"
echo "  kubectl -n $NAMESPACE get pods"
echo "  kubectl -n $NAMESPACE port-forward svc/$RELEASE_NAME-auth-service 8443:443"
echo "  kubectl -n $NAMESPACE port-forward svc/$RELEASE_NAME-project-service 8444:443"

