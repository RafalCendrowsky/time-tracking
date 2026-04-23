[CmdletBinding()]
param(
    [string]$ReleaseName    = 'time-tracking',
    [string]$VaultNamespace = 'vault',
    [string]$AppReleaseName  = 'time-tracking',
    [string]$AppNamespace    = 'time-tracking',
    [int]   $KeyShares      = 5,
    [int]   $KeyThreshold   = 3,
    [string]$ClientSecret   = 'base-client-secret',
    [string]$OutputFile     = '',
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
    param(
        [string]$Command,
        [string]$InputText = $null
    )
    # Run a shell snippet inside the Vault pod.
    # TLS is self-signed; VAULT_SKIP_VERIFY=true is safe here because we are
    # connecting to 127.0.0.1 inside the pod itself.
    $ErrorActionPreference = "SilentlyContinue"
    if ($null -ne $InputText) {
        $output = $InputText | & kubectl exec -i -n $VaultNamespace $script:PodName `
            -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_SKIP_VERIFY=true $Command" 2>&1
    } else {
        $output = & kubectl exec -n $VaultNamespace $script:PodName `
            -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_SKIP_VERIFY=true $Command" 2>&1
    }
    $ErrorActionPreference = "Stop"
    return [PSCustomObject]@{ Output = $output; ExitCode = $LASTEXITCODE }
}


function New-AuthServiceRsaJwkJson {

    function Convert-ToBase64Url {
        param([byte[]]$Bytes)
        [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    }

    $rsa = [System.Security.Cryptography.RSA]::Create(2048)
    try {
        $p = $rsa.ExportParameters($true)
        $jwk = [ordered]@{
            kty = 'RSA'
            use = 'sig'
            alg = 'RS256'
            kid = [guid]::NewGuid().ToString('N')
            n   = Convert-ToBase64Url $p.Modulus
            e   = Convert-ToBase64Url $p.Exponent
            d   = Convert-ToBase64Url $p.D
            p   = Convert-ToBase64Url $p.P
            q   = Convert-ToBase64Url $p.Q
            dp  = Convert-ToBase64Url $p.DP
            dq  = Convert-ToBase64Url $p.DQ
            qi  = Convert-ToBase64Url $p.InverseQ
        }

        return ($jwk | ConvertTo-Json -Compress)
    } finally {
        $rsa.Dispose()
    }
}

function Seed-AuthServiceRsaJwk {
    param([Parameter(Mandatory = $true)][string]$RootToken)

    $vaultPath = 'secret/auth-service/jwt-rsa-key'

    $checkResult = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault kv metadata get -format=json $vaultPath"
    if ($checkResult.ExitCode -eq 0) {
        $metadata = ($checkResult.Output | Where-Object { $_ }) -join "`n" | ConvertFrom-Json
        $currentVersion = [string]$metadata.data.current_version
        $versionInfo = $metadata.data.versions.PSObject.Properties[$currentVersion].Value

        if ($currentVersion -and $versionInfo -and -not $versionInfo.deletion_time -and -not $versionInfo.destroyed) {
            Write-Host "Auth service RSA JWK already exists and is active in Vault at '$vaultPath'."
            return
        }

        Write-Host "Auth service RSA JWK at '$vaultPath' is deleted or destroyed; reseeding it."
    } elseif ($checkResult.ExitCode -ne 2) {
        throw "vault kv metadata get $vaultPath failed:`n$($checkResult.Output)"
    }


    Write-Host "Seeding auth service RSA JWK in Vault at '$vaultPath'..."
    $jwkJson = New-AuthServiceRsaJwkJson
    $writeResult = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault kv put $vaultPath value=`$(cat)" -InputText $jwkJson
    if ($writeResult.ExitCode -ne 0) {
        throw "vault kv put $vaultPath failed:`n$($writeResult)"
    }

    Write-Host "Auth service RSA JWK seeded successfully."
}

function Seed-AuthServiceClientSecret {
    param(
        [Parameter(Mandatory = $true)][string]$RootToken,
        [string]$ClientSecret = ''
    )

    $vaultPath = 'secret/auth-service/oauth2-client-secret'

    $checkResult = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault kv metadata get -format=json $vaultPath"
    if ($checkResult.ExitCode -eq 0) {
        $metadata = ($checkResult.Output | Where-Object { $_ }) -join "`n" | ConvertFrom-Json
        $currentVersion = [string]$metadata.data.current_version
        $versionInfo = $metadata.data.versions.PSObject.Properties[$currentVersion].Value

        if ($currentVersion -and $versionInfo -and -not $versionInfo.deletion_time -and -not $versionInfo.destroyed) {
            Write-Host "OAuth2 client secret already exists and is active in Vault at '$vaultPath'."
            return
        }

        Write-Host "OAuth2 client secret at '$vaultPath' is deleted or destroyed; reseeding it."
    } elseif ($checkResult.ExitCode -ne 2) {
        throw "vault kv metadata get $vaultPath failed:`n$($checkResult.Output)"
    }

    if ([string]::IsNullOrWhiteSpace($ClientSecret)) {
        Write-Warning "OAuth2 client secret is missing from Vault at '$vaultPath', but no ClientSecret value was provided; skipping seed."
        return
    }

    Write-Host "Seeding OAuth2 client secret in Vault at '$vaultPath'..."
    $writeResult = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault kv put $vaultPath value=$ClientSecret"
    if ($writeResult.ExitCode -ne 0) {
        throw "vault kv put $vaultPath failed:`n$($writeResult.Output)"
    }

    Write-Host "OAuth2 client secret seeded successfully."
}

function Ensure-SecretKvMount {
    param([Parameter(Mandatory = $true)][string]$RootToken)

    $mountsResult = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault secrets list -detailed -format=json"
    if ($mountsResult.ExitCode -ne 0) {
        throw "vault secrets list failed:`n$($mountsResult.Output)"
    }

    $mounts = ($mountsResult.Output | Where-Object { $_ }) -join "`n" | ConvertFrom-Json
    $secretMount = $mounts.'secret/'

    if ($null -eq $secretMount) {
        Write-Host "Enabling KV v2 mount at 'secret/'..."
        $enableResult = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault secrets enable -path=secret kv-v2"
        if ($enableResult.ExitCode -ne 0) {
            throw "vault secrets enable failed:`n$($enableResult.Output)"
        }
        return
    }

    if ($secretMount.type -ne 'kv' -or $secretMount.options.version -ne '2') {
        throw "Vault mount 'secret/' already exists but is not KV v2."
    }

    Write-Host "KV v2 mount 'secret/' already exists."
}

function Configure-VaultKubernetesAuth {
    param([Parameter(Mandatory = $true)][string]$RootToken)

    $roleName = "$AppReleaseName-auth-service"
    $policyName = 'auth-service'
    $policy = @"
path `"secret/data/auth-service/*`" {
  capabilities = [ `"read`" ]
}
"@

    Write-Host "Configuring Vault Kubernetes auth for role '$roleName'"

    $enableAuth = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault auth enable kubernetes || true"
    if ($enableAuth.ExitCode -ne 0) {
        throw "vault auth enable kubernetes failed:`n$($enableAuth.Output)"
    }

    $policyWrite = Invoke-VaultExec -Command "VAULT_TOKEN=$RootToken vault policy write $policyName -" -InputText $policy
    if ($policyWrite.ExitCode -ne 0) {
        throw "vault policy write $policyName failed:`n$($policyWrite.Output)"
    }

    $configCmd = "VAULT_TOKEN=$RootToken vault write auth/kubernetes/config kubernetes_host=https://kubernetes.default.svc:443 kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token"
    $configResult = Invoke-VaultExec -Command $configCmd
    if ($configResult.ExitCode -ne 0) {
        throw "vault write auth/kubernetes/config failed:`n$($configResult.Output)"
    }

    $roleCmd = "VAULT_TOKEN=$RootToken vault write auth/kubernetes/role/$roleName bound_service_account_names=$roleName bound_service_account_namespaces=$AppNamespace token_policies=$policyName ttl=1h"
    $roleResult = Invoke-VaultExec -Command $roleCmd
    if ($roleResult.ExitCode -ne 0) {
        throw "vault write auth/kubernetes/role/$roleName failed:`n$($roleResult.Output)"
    }
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

if (-not $rootToken -and (Test-Path $UnsealKeysFile)) {
    Write-Host "Loading bootstrap data from $UnsealKeysFile"
    $stored = Get-Content $UnsealKeysFile -Raw | ConvertFrom-Json
    $rootToken = $stored.root_token
}

if ($rootToken) {
    Ensure-SecretKvMount -RootToken $rootToken
    Seed-AuthServiceRsaJwk -RootToken $rootToken
    Seed-AuthServiceClientSecret -RootToken $rootToken -ClientSecret $ClientSecret
    Configure-VaultKubernetesAuth -RootToken $rootToken
} else {
    Write-Warning "No root token available; skipping Vault Kubernetes auth bootstrap."
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
Write-Host "  auth-service role: $AppNamespace / $AppReleaseName-auth-service"

