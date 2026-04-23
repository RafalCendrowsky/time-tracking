#!/usr/bin/env bash
set -euo pipefail

# Lightweight script to deploy only the application (auth/project) into an existing cluster
# - Does NOT install operators, cert-manager, the shared CA chart, or Vault
# - Expects the shared CA and Vault to already be installed (Vault in the dedicated `vault` namespace)
# - By default builds & loads local images for the two services; pass --skip-images to skip that
# - Use --rebuild-images to force rebuild

CLUSTER_NAME="time-tracking-kind"
NAMESPACE="time-tracking"
RELEASE_NAME="time-tracking"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART_PATH="${REPO_ROOT}/helm/time-tracking"
VALUES_FILE="${CHART_PATH}/values.yaml"
REGISTRY=""
REBUILD_IMAGES=false
SKIP_IMAGES=false

usage(){ cat <<'EOF'
Usage: deploy-app.sh [--rebuild-images] [--skip-images] [--cluster CLUSTER] [--namespace NAMESPACE]

Options:
  --rebuild-images    Force rebuild auth/project images before loading into kind
  --skip-images       Do not build or load images (assume images already in cluster/registry)
  --cluster NAME      Kind cluster name
  --namespace NAME    Kubernetes namespace
  -h, --help          Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rebuild-images) REBUILD_IMAGES=true; shift ;;
    --skip-images) SKIP_IMAGES=true; shift ;;
    --cluster) CLUSTER_NAME="$2"; shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
done

require_cmd(){ command -v "$1" >/dev/null 2>&1 || { echo "Required command '$1' not found" >&2; exit 1; } }
require_cmd kind; require_cmd kubectl; require_cmd helm; require_cmd docker

image_name(){ local repository="$1"; if [[ -z "$REGISTRY" ]]; then printf '%s' "$repository"; else printf '%s/%s' "$REGISTRY" "$repository"; fi }

image_exists(){ docker image inspect "$1" >/dev/null 2>&1 || return 1 }

ensure_image(){ local image="$1"; local context_path="$2";
  if [[ "$REBUILD_IMAGES" == "true" ]] || ! image_exists "$image"; then
    echo "Building $image from $context_path"
    docker build -t "$image" "$context_path"
  else
    echo "Reusing existing local image $image"
  fi
  archive_path="$(mktemp -t "${CLUSTER_NAME}-$(basename "$image").XXXXXX.tar")"
  docker save -o "$archive_path" "$image"
  kind load image-archive "$archive_path" --name "$CLUSTER_NAME"
  rm -f "$archive_path"
}

if [[ ! -d "$CHART_PATH" ]]; then echo "Chart path $CHART_PATH not found" >&2; exit 1; fi
if [[ ! -f "$VALUES_FILE" ]]; then echo "Values file $VALUES_FILE not found" >&2; exit 1; fi

SECRETS_FILE="$(dirname "$VALUES_FILE")/secrets.yaml"

if [[ "$SKIP_IMAGES" != "true" ]]; then
  ensure_image "$(image_name 'time-tracking/auth-service:latest')" "${REPO_ROOT}/services/auth-service"
  ensure_image "$(image_name 'time-tracking/project-service:latest')" "${REPO_ROOT}/services/project-service"
else
  echo "Skipping image build/load as requested"
fi

if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
  kubectl create namespace "$NAMESPACE"
fi

# Deploy umbrella chart but skip CRDs (operators must already be installed)
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

helm "${helm_args[@]}"

for deployment in "${RELEASE_NAME}-auth-service" "${RELEASE_NAME}-project-service"; do
  echo "Restarting deployment ${deployment}"
  kubectl rollout restart "deployment/${deployment}" -n "$NAMESPACE"
  kubectl rollout status "deployment/${deployment}" -n "$NAMESPACE" --timeout=5m
done

echo "Application deployment complete."
echo "kubectl -n $NAMESPACE get pods"

