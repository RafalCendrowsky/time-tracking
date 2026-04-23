[CmdletBinding()]
param(
    [string]$ReleaseName    = 'time-tracking',
    [string]$VaultNamespace = 'vault',
    [int]   $KeyShares      = 5,
    [int]   $KeyThreshold   = 3,
    # File where the init output (unseal keys + root token) is persisted.
    # Defaults to <script-dir>/vault-init.json.  KEEP THIS FILE SECURE.
    [string]$OutputFile     = '',
    # Existing init-output file to read unseal keys from when Vault is already
    # initialised but sealed (e.g. after a pod restart).
    [string]$UnsealKeysFile = '',
    [int]   $TimeoutSec     = 180
)

$ErrorActionPreference = 'Stop'

# -- helpers -------------------------------------------------------------------

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found on PATH."
    }
}

function Invoke-VaultExec {
    param([string]$Command)
    # Run a shell snippet inside the Vault pod.
    # TLS is self-signed; VAULT_SKIP_VERIFY=true is safe here because we are
    # connecting to 127.0.0.1 inside the pod itself.
    $ErrorActionPreference = "SilentlyContinue"
    $output = & kubectl exec -n $VaultNamespace $script:PodName `
        -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_SKIP_VERIFY=true $Command" 2>&1
    $ErrorActionPreference = "Stop"
    return [PSCustomObject]@{ Output = $output; ExitCode = $LASTEXITCODE }
}

function Wait-ForPodRunning {
    param([int]$TimeoutSec = 180)
    Write-Host "Waiting for pod '$script:PodName' to be Running in namespace '$VaultNamespace'..."
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $phase = & kubectl get pod $script:PodName -n $VaultNamespace `
            -o jsonpath='{.status.phase}' 2>$null
        if ($phase -eq 'Running') {
            Write-Host "Pod is Running."
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "Timed out waiting for pod '$script:PodName' to reach Running phase."
}

Assert-Command -Name 'kubectl'

$script:PodName = "$ReleaseName-vault-0"

if ([string]::IsNullOrWhiteSpace($OutputFile)) {
    $OutputFile = Join-Path $PSScriptRoot 'vault-init-keys.json'
}
if ([string]::IsNullOrWhiteSpace($UnsealKeysFile)) {
    $UnsealKeysFile = $OutputFile
}


Wait-ForPodRunning -TimeoutSec $TimeoutSec

Write-Host "Checking Vault initialisation status..."
$initStatus = Invoke-VaultExec -Command 'vault operator init -status -format=json'

$unsealKeys  = @()
$rootToken   = $null

if ($initStatus.ExitCode -eq 2) {
    Write-Host "Vault is NOT initialised. Initialising with $KeyShares key shares (threshold $KeyThreshold)..."

    $initResult = Invoke-VaultExec -Command `
        "vault operator init -key-shares=$KeyShares -key-threshold=$KeyThreshold -format=json"

    if ($initResult.ExitCode -ne 0) {
        throw "vault operator init failed:`n$($initResult.Output)"
    }

    $initJson = ($initResult.Output | Where-Object { $_ } ) -join "`n"

    Write-Host "Saving init output to $OutputFile"
    Set-Content -Path $OutputFile -Value $initJson -Encoding UTF8
    Write-Warning "IMPORTANT: $OutputFile contains unseal keys and the root token. Store it securely and remove it when done."

    $parsed    = $initJson | ConvertFrom-Json
    $unsealKeys = $parsed.unseal_keys_b64
    $rootToken  = $parsed.root_token

    Write-Host "Vault initialised successfully."

} elseif ($initStatus.ExitCode -eq 0) {
    Write-Host "Vault is already initialised."
} else {
    throw "vault operator init -status returned unexpected exit code $($initStatus.ExitCode):`n$($initStatus.Output)"
}

Write-Host "Checking Vault seal status..."
$sealCheck = Invoke-VaultExec -Command 'vault status -format=json'

if ($sealCheck.ExitCode -eq 0) {
    Write-Host "Vault is already UNSEALED. Nothing to do."
} elseif ($sealCheck.ExitCode -eq 2) {
    Write-Host "Vault is SEALED. Proceeding with unseal..."

    if ($unsealKeys.Count -eq 0) {
        if (Test-Path $UnsealKeysFile) {
            Write-Host "Loading unseal keys from $UnsealKeysFile"
            $stored    = Get-Content $UnsealKeysFile -Raw | ConvertFrom-Json
            $unsealKeys = $stored.unseal_keys_b64
            $rootToken  = $stored.root_token
        } else {
            Write-Host "No unseal-keys file found at '$UnsealKeysFile'."
            Write-Host "Please enter $KeyThreshold unseal key(s). Press Enter after each."
            for ($i = 1; $i -le $KeyThreshold; $i++) {
                $k = Read-Host "Unseal key $i"
                $unsealKeys += $k
            }
        }
    }

    $keysToApply = $unsealKeys | Select-Object -First $KeyThreshold
    foreach ($key in $keysToApply) {
        Write-Host "Applying unseal key ($([array]::IndexOf($keysToApply, $key) + 1)/$($keysToApply.Count))..."
        $unsealResult = Invoke-VaultExec -Command "vault operator unseal $key"
        if ($unsealResult.ExitCode -ne 0) {
            throw "vault operator unseal failed:`n$($unsealResult.Output)"
        }
    }

    $finalCheck = Invoke-VaultExec -Command 'vault status -format=json'
    if ($finalCheck.ExitCode -eq 0) {
        Write-Host "Vault is now UNSEALED."
    } else {
        $statusJson = ($finalCheck.Output | Where-Object { $_ }) -join "`n" | ConvertFrom-Json -ErrorAction SilentlyContinue
        $progress   = if ($statusJson) { "Unseal progress: $($statusJson.unseal_progress)/$($statusJson.unseal_threshold)" } else { '' }
        Write-Warning "Vault is still sealed after applying keys. $progress"
        Write-Warning "You may need to provide additional unseal keys."
    }
} else {
    throw "vault status returned unexpected exit code $($sealCheck.ExitCode):`n$($sealCheck.Output)"
}


Write-Host ''
Write-Host ' Vault init/unseal complete'
if ($rootToken) {
    Write-Host "Root token: $rootToken"
    Write-Host "(Also saved in $OutputFile)"
} else {
    Write-Host "Root token: (not available here - check $UnsealKeysFile)"
}
Write-Host ''
Write-Host 'Useful follow-up commands:'
Write-Host "  kubectl -n $VaultNamespace exec $script:PodName -- sh -c 'VAULT_ADDR=https://127.0.0.1:8200 VAULT_SKIP_VERIFY=true VAULT_TOKEN=[root-token] vault status'"
Write-Host "  kubectl -n $VaultNamespace port-forward svc/$ReleaseName-vault 8200:8200"

