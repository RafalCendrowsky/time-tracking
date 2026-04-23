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
#   --client-secret VALUE    OAuth2 client secret to seed if missing
#   --timeout N              Seconds to wait for pod ready (default: 180)
#   -h, --help               Show this help
#
# The init output (unseal keys + root token) is saved to --output-file.
# KEEP THAT FILE SECURE – delete it once you have stored the keys safely.
set -euo pipefail

RELEASE_NAME="time-tracking"
VAULT_NAMESPACE="vault"
APP_RELEASE_NAME="time-tracking"
APP_NAMESPACE="time-tracking"
KEY_SHARES=5
KEY_THRESHOLD=3
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="${SCRIPT_DIR}/vault-init-keys.json"
CLIENT_SECRET=""
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
    --app-release-name) APP_RELEASE_NAME="$2"; shift 2 ;;
    --app-namespace)    APP_NAMESPACE="$2";    shift 2 ;;
    --key-shares)      KEY_SHARES="$2";      shift 2 ;;
    --key-threshold)   KEY_THRESHOLD="$2";   shift 2 ;;
    --output-file)     OUTPUT_FILE="$2";     shift 2 ;;
    --unseal-keys-file) UNSEAL_KEYS_FILE="$2"; shift 2 ;;
    --client-secret)   CLIENT_SECRET="$2";   shift 2 ;;
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

vault_exec_with_input() {
  local input_text="$1"
  shift
  printf '%s' "$input_text" | kubectl exec -i -n "$VAULT_NAMESPACE" "$POD_NAME" \
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

configure_vault_kubernetes_auth() {
  local root_token="$1"
  local role_name="${APP_RELEASE_NAME}-auth-service"
  local policy_name="auth-service"
  local policy=$'path "secret/data/auth-service/*" {\n  capabilities = ["read"]\n}\n\npath "secret/metadata/auth-service/*" {\n  capabilities = ["list"]\n}\n'

  echo "Configuring Vault Kubernetes auth for role '${role_name}'"

  vault_exec "VAULT_TOKEN=${root_token} vault auth enable kubernetes || true" >/dev/null
  vault_exec_with_input "$policy" "VAULT_TOKEN=${root_token} vault policy write ${policy_name} -" >/dev/null
  vault_exec "VAULT_TOKEN=${root_token} vault write auth/kubernetes/config kubernetes_host=https://kubernetes.default.svc:443 kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token" >/dev/null
  vault_exec "VAULT_TOKEN=${root_token} vault write auth/kubernetes/role/${role_name} bound_service_account_names=${role_name} bound_service_account_namespaces=${APP_NAMESPACE} token_policies=${policy_name} ttl=1h" >/dev/null
}

seed_auth_service_rsa_jwk() {
  local root_token="$1"
  local vault_path="secret/auth-service/jwt-rsa-key"

  if vault_exec "VAULT_TOKEN=${root_token} vault kv metadata get -format=json ${vault_path}" \
    | python3 -c 'import json,sys; doc=json.load(sys.stdin); data=doc.get("data", {}); current=str(data.get("current_version", "")); versions=data.get("versions", {}); entry=versions.get(current, {}); sys.exit(0 if current and not entry.get("deletion_time") and not entry.get("destroyed") else 1)'; then
    echo "Auth service RSA JWK already exists and is active in Vault at '${vault_path}'."
    return 0
  fi

  echo "Seeding auth service RSA JWK in Vault at '${vault_path}'..."
  local jwk_json
  jwk_json="$(generate_auth_service_rsa_jwk_json)"
  vault_exec_with_input "$jwk_json" "VAULT_TOKEN=${root_token} vault kv put ${vault_path} value=\$(cat)" >/dev/null
  echo "Auth service RSA JWK seeded successfully."
}

generate_auth_service_rsa_jwk_json() {
  require_cmd openssl
  python3 - <<'PY'
import base64
import json
import os
import re
import subprocess
import tempfile
import uuid

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

def parse_openssl_text(text: str):
    sections = {}
    current = None
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line in {"modulus:", "privateExponent:", "prime1:", "prime2:", "exponent1:", "exponent2:", "coefficient:"}:
            current = line[:-1]
            sections[current] = []
            continue
        m = re.match(r"^publicExponent:\s+(\d+)", line)
        if m:
            sections["publicExponent"] = int(m.group(1))
            current = None
            continue
        if current and re.fullmatch(r"[0-9A-Fa-f: ]+", line):
            sections[current].append(line)
            continue
        current = None
    return sections

tmp = tempfile.NamedTemporaryFile(delete=False)
tmp.close()
try:
    subprocess.run(["openssl", "genrsa", "-out", tmp.name, "2048"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    proc = subprocess.run(["openssl", "rsa", "-in", tmp.name, "-text", "-noout"], check=True, capture_output=True, text=True)
    sections = parse_openssl_text(proc.stdout + "\n" + proc.stderr)

    required = ["modulus", "privateExponent", "prime1", "prime2", "exponent1", "exponent2", "coefficient", "publicExponent"]
    missing = [name for name in required if name not in sections]
    if missing:
        raise SystemExit(f"Failed to parse RSA key components from openssl output: {', '.join(missing)}")

    def hex_bytes(name: str) -> bytes:
        return bytes.fromhex("".join(sections[name]).replace(":", "").replace(" ", ""))

    public_exponent = sections["publicExponent"]
    exponent_bytes = public_exponent.to_bytes((public_exponent.bit_length() + 7) // 8 or 1, "big")

    jwk = {
        "kty": "RSA",
        "use": "sig",
        "alg": "RS256",
        "kid": uuid.uuid4().hex,
        "n": b64url(hex_bytes("modulus")),
        "e": b64url(exponent_bytes),
        "d": b64url(hex_bytes("privateExponent")),
        "p": b64url(hex_bytes("prime1")),
        "q": b64url(hex_bytes("prime2")),
        "dp": b64url(hex_bytes("exponent1")),
        "dq": b64url(hex_bytes("exponent2")),
        "qi": b64url(hex_bytes("coefficient")),
    }

    print(json.dumps(jwk, separators=(",", ":")))
finally:
    try:
        os.unlink(tmp.name)
    except FileNotFoundError:
        pass
PY
}

seed_auth_service_client_secret() {
  local root_token="$1"
  local client_secret="$2"
  local vault_path="secret/auth-service/oauth2-client-secret"

  if vault_exec "VAULT_TOKEN=${root_token} vault kv metadata get -format=json ${vault_path}" \
    | python3 -c 'import json,sys; doc=json.load(sys.stdin); data=doc.get("data", {}); current=str(data.get("current_version", "")); versions=data.get("versions", {}); entry=versions.get(current, {}); sys.exit(0 if current and not entry.get("deletion_time") and not entry.get("destroyed") else 1)'; then
    echo "OAuth2 client secret already exists and is active in Vault at '${vault_path}'."
    return 0
  fi

  if [[ -z "$client_secret" ]]; then
    echo "OAuth2 client secret is missing from Vault at '${vault_path}', but no --client-secret value was provided; skipping seed." >&2
    return 0
  fi

  echo "Seeding OAuth2 client secret in Vault at '${vault_path}'..."
  vault_exec "VAULT_TOKEN=${root_token} vault kv put ${vault_path} value=${client_secret}" >/dev/null
  echo "OAuth2 client secret seeded successfully."
}

ensure_secret_kv_mount() {
  local root_token="$1"

  if vault_exec "VAULT_TOKEN=${root_token} vault secrets list -detailed -format=json" | python3 -c 'import json,sys; mounts=json.load(sys.stdin); sys.exit(0 if "secret/" in mounts and mounts["secret/"].get("type") == "kv" and mounts["secret/"].get("options", {}).get("version") == "2" else 1)'; then
    echo "KV v2 mount 'secret/' already exists."
    return 0
  fi

  echo "Enabling KV v2 mount at 'secret/'..."
  vault_exec "VAULT_TOKEN=${root_token} vault secrets enable -path=secret kv-v2" >/dev/null
}


require_cmd kubectl
require_cmd python3



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

if [[ -n "$ROOT_TOKEN" ]]; then
  ensure_secret_kv_mount "$ROOT_TOKEN"
  seed_auth_service_rsa_jwk "$ROOT_TOKEN"
  seed_auth_service_client_secret "$ROOT_TOKEN" "$CLIENT_SECRET"
  configure_vault_kubernetes_auth "$ROOT_TOKEN"
else
  echo "No root token available; skipping Vault Kubernetes auth bootstrap." >&2
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
echo "  auth-service role: ${APP_NAMESPACE} / ${APP_RELEASE_NAME}-auth-service"


