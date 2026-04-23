#!/usr/bin/env bash
# init-vault.sh – initialise and/or unseal Vault running in Kubernetes.
#
# Usage:
#   ./init-vault.sh [options]
#
# Options:
#   --release-name NAME      Helm release name (default: time-tracking)
#   --namespace NAME         Vault namespace   (default: vault)
#   --key-shares N           Shamir key shares  (default: 5)
#   --key-threshold N        Unseal threshold   (default: 3)
#   --output-file PATH       Where to save init JSON (default: <script-dir>/vault-init.json)
#   --unseal-keys-file PATH  Existing init JSON to read unseal keys from
#   --timeout N              Seconds to wait for pod ready (default: 180)
#   -h, --help               Show this help
#
# The init output (unseal keys + root token) is saved to --output-file.
# KEEP THAT FILE SECURE – delete it once you have stored the keys safely.
set -euo pipefail

RELEASE_NAME="time-tracking"
VAULT_NAMESPACE="vault"
KEY_SHARES=5
KEY_THRESHOLD=3
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="${SCRIPT_DIR}/vault-init-keys.json"
UNSEAL_KEYS_FILE=""
TIMEOUT_SEC=180


usage() {
  grep '^#' "$0" | grep -v '#!/' | sed 's/^# \{0,1\}//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-name)    RELEASE_NAME="$2";    shift 2 ;;
    --namespace)       VAULT_NAMESPACE="$2"; shift 2 ;;
    --key-shares)      KEY_SHARES="$2";      shift 2 ;;
    --key-threshold)   KEY_THRESHOLD="$2";   shift 2 ;;
    --output-file)     OUTPUT_FILE="$2";     shift 2 ;;
    --unseal-keys-file) UNSEAL_KEYS_FILE="$2"; shift 2 ;;
    --timeout)         TIMEOUT_SEC="$2";     shift 2 ;;
    -h|--help)         usage ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

[[ -z "$UNSEAL_KEYS_FILE" ]] && UNSEAL_KEYS_FILE="$OUTPUT_FILE"

POD_NAME="${RELEASE_NAME}-vault-0"

require_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "Required command '$1' not found on PATH." >&2; exit 1; }; }

vault_exec() {
  kubectl exec -n "$VAULT_NAMESPACE" "$POD_NAME" \
    -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_SKIP_VERIFY=true $*"
}

parse_json_array() {
  local file="$1" field="$2"
  if command -v jq >/dev/null 2>&1; then
    jq -r ".${field}[]" "$file"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c "import json,sys; [print(k) for k in json.load(open('${file}'))['${field}']]"
  else
    echo "Neither jq nor python3 found – cannot parse JSON. Install one of them." >&2
    exit 1
  fi
}

parse_json_string() {
  local file="$1" field="$2"
  if command -v jq >/dev/null 2>&1; then
    jq -r ".${field}" "$file"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c "import json; print(json.load(open('${file}'))['${field}'])"
  else
    echo "Neither jq nor python3 found – cannot parse JSON. Install one of them." >&2
    exit 1
  fi
}

wait_for_pod() {
  echo "Waiting for pod '$POD_NAME' to be Running in namespace '$VAULT_NAMESPACE'..."
  local deadline=$(( $(date +%s) + TIMEOUT_SEC ))
  while [[ $(date +%s) -lt $deadline ]]; do
    local phase
    phase=$(kubectl get pod "$POD_NAME" -n "$VAULT_NAMESPACE" \
      -o jsonpath='{.status.phase}' 2>/dev/null || true)
    if [[ "$phase" == "Running" ]]; then
      echo "Pod is Running."
      return 0
    fi
    sleep 3
  done
  echo "Timed out waiting for pod '$POD_NAME' to reach Running phase." >&2
  exit 1
}


require_cmd kubectl


wait_for_pod

echo "Checking Vault initialisation status..."
UNSEAL_KEYS=()
ROOT_TOKEN=""

set +e
vault_exec 'vault operator init -status -format=json' >/dev/null 2>&1
INIT_STATUS_EXIT=$?
set -e

if [[ $INIT_STATUS_EXIT -eq 2 ]]; then
  echo "Vault is NOT initialised. Initialising with ${KEY_SHARES} key shares (threshold ${KEY_THRESHOLD})..."

  INIT_JSON=$(vault_exec \
    "vault operator init -key-shares=${KEY_SHARES} -key-threshold=${KEY_THRESHOLD} -format=json")

  echo "Saving init output to ${OUTPUT_FILE}"
  printf '%s\n' "$INIT_JSON" > "$OUTPUT_FILE"
  chmod 600 "$OUTPUT_FILE"
  echo "WARNING: ${OUTPUT_FILE} contains unseal keys and the root token. Store it securely and remove it when done." >&2

  ROOT_TOKEN=$(parse_json_string "$OUTPUT_FILE" "root_token")
  mapfile -t UNSEAL_KEYS < <(parse_json_array "$OUTPUT_FILE" "unseal_keys_b64")

  echo "Vault initialised successfully."

elif [[ $INIT_STATUS_EXIT -eq 0 ]]; then
  echo "Vault is already initialised."
else
  echo "vault operator init -status returned unexpected exit code ${INIT_STATUS_EXIT}." >&2
  exit 1
fi


echo "Checking Vault seal status..."

set +e
vault_exec 'vault status -format=json' >/dev/null 2>&1
SEAL_EXIT=$?
set -e

if [[ $SEAL_EXIT -eq 0 ]]; then
  echo "Vault is already UNSEALED. Nothing to do."

elif [[ $SEAL_EXIT -eq 2 ]]; then
  echo "Vault is SEALED. Proceeding with unseal..."

  if [[ ${#UNSEAL_KEYS[@]} -eq 0 ]]; then
    if [[ -f "$UNSEAL_KEYS_FILE" ]]; then
      echo "Loading unseal keys from ${UNSEAL_KEYS_FILE}"
      mapfile -t UNSEAL_KEYS < <(parse_json_array "$UNSEAL_KEYS_FILE" "unseal_keys_b64")
      ROOT_TOKEN=$(parse_json_string "$UNSEAL_KEYS_FILE" "root_token")
    else
      echo "No unseal-keys file found at '${UNSEAL_KEYS_FILE}'."
      echo "Please enter ${KEY_THRESHOLD} unseal key(s):"
      for (( i=1; i<=KEY_THRESHOLD; i++ )); do
        read -rp "Unseal key ${i}: " key
        UNSEAL_KEYS+=("$key")
      done
    fi
  fi

  count=0
  for key in "${UNSEAL_KEYS[@]}"; do
    if [[ $count -ge $KEY_THRESHOLD ]]; then break; fi
    echo "Applying unseal key $(( count + 1 ))/${KEY_THRESHOLD}..."
    vault_exec "vault operator unseal ${key}"
    (( count++ )) || true
  done

  set +e
  vault_exec 'vault status -format=json' >/dev/null 2>&1
  FINAL_EXIT=$?
  set -e

  if [[ $FINAL_EXIT -eq 0 ]]; then
    echo "Vault is now UNSEALED."
  else
    echo "WARNING: Vault still appears to be sealed after applying keys." >&2
    echo "You may need to provide additional unseal keys." >&2
  fi

else
  echo "vault status returned unexpected exit code ${SEAL_EXIT}." >&2
  exit 1
fi


echo ""
echo " Vault init/unseal complete"
if [[ -n "$ROOT_TOKEN" ]]; then
  echo "Root token : ${ROOT_TOKEN}"
  echo "(Also saved in ${OUTPUT_FILE})"
else
  echo "Root token : (not available here – check ${UNSEAL_KEYS_FILE})"
fi
echo ""
echo "Useful follow-up commands:"
echo "  kubectl -n ${VAULT_NAMESPACE} exec ${POD_NAME} -- sh -c 'VAULT_ADDR=https://127.0.0.1:8200 VAULT_SKIP_VERIFY=true VAULT_TOKEN=<root-token> vault status'"
echo "  kubectl -n ${VAULT_NAMESPACE} port-forward svc/${RELEASE_NAME}-vault 8200:8200"


