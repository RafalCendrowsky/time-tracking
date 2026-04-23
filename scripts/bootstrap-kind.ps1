[CmdletBinding()]
param(
    [string]$ClusterName = 'time-tracking-kind',
    [string]$Namespace = 'time-tracking',
    [string]$ReleaseName = 'time-tracking',
    [string]$ChartPath = '',
    [string]$ValuesFile = '',
    [string]$Registry = '',
    [switch]$RebuildImages
)

$ErrorActionPreference = 'Stop'

function Assert-Command
{
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue))
    {
        throw "Required command '$Name' was not found on PATH."
    }
}

function Invoke-Checked
{
    param(
        [Parameter(Mandatory = $true)][scriptblock]$ScriptBlock,
        [Parameter(Mandatory = $true)][string]$Description
    )

    & $ScriptBlock
    if ($LASTEXITCODE -ne 0)
    {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Get-ImageName
{
    param([Parameter(Mandatory = $true)][string]$Repository)

    if ( [string]::IsNullOrWhiteSpace($Registry))
    {
        return $Repository
    }

    return ($Registry.TrimEnd('/') + '/' + $Repository)
}

function Test-DockerImageExists
{
    param([Parameter(Mandatory = $true)][string]$Image)

    try
    {
        & docker image inspect $Image 1> $null 2> $null
        return ($LASTEXITCODE -eq 0)
    }
    catch
    {
        return $false
    }
}

function Ensure-DockerImage
{
    param(
        [Parameter(Mandatory = $true)][string]$Image,
        [Parameter(Mandatory = $true)][string]$ContextPath
    )

    if ($RebuildImages -or -not (Test-DockerImageExists -Image $Image))
    {
        Write-Host "Building $Image from $ContextPath"
        Invoke-Checked -Description "docker build for $Image" -ScriptBlock {
            & docker build -t $Image $ContextPath
        }
    }
    else
    {
        Write-Host "Reusing existing local image $Image"
    }

    $archivePath = [System.IO.Path]::Combine($env:TEMP, ([System.Guid]::NewGuid().ToString() + '.tar'))
    try
    {
        Write-Host "Saving $Image to $archivePath"
        Invoke-Checked -Description "docker save for $Image" -ScriptBlock {
            & docker save -o $archivePath $Image
        }

        Write-Host "Loading $Image archive into kind cluster $ClusterName"
        Invoke-Checked -Description "kind load image-archive $Image" -ScriptBlock {
            & kind load image-archive $archivePath --name $ClusterName
        }
    }
    finally
    {
        if (Test-Path $archivePath)
        {
            Remove-Item $archivePath -Force -ErrorAction SilentlyContinue
        }
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ( [string]::IsNullOrWhiteSpace($ChartPath))
{
    $ChartPath = Join-Path $repoRoot 'helm\time-tracking'
}
if ( [string]::IsNullOrWhiteSpace($ValuesFile))
{
    $ValuesFile = Join-Path $ChartPath 'values.yaml'
}
$SharedCAChartPath = Join-Path $repoRoot 'helm\shared-ca'
$SharedCAValuesFile = Join-Path $SharedCAChartPath 'values.yaml'
$VaultChartPath = Join-Path $repoRoot 'helm\vault'
$VaultValuesFile = Join-Path $VaultChartPath 'values.yaml'
$VaultInitKeysFile = Join-Path $PSScriptRoot 'vault-init-keys.json'

Assert-Command -Name 'kind'
Assert-Command -Name 'kubectl'
Assert-Command -Name 'helm'
Assert-Command -Name 'docker'

$clusterList = @(& kind get clusters)
if (-not ($clusterList -contains $ClusterName))
{
    Write-Host "Creating kind cluster '$ClusterName'"
    Invoke-Checked -Description "kind create cluster" -ScriptBlock {
        & kind create cluster --name $ClusterName
    }
}
else
{
    Write-Host "Kind cluster '$ClusterName' already exists"
}

Write-Host 'Adding Helm repositories'
Invoke-Checked -Description 'helm repo add jetstack' -ScriptBlock { & helm repo add jetstack https://charts.jetstack.io --force-update }
Invoke-Checked -Description 'helm repo add cnpg' -ScriptBlock { & helm repo add cnpg https://cloudnative-pg.github.io/charts --force-update }
Invoke-Checked -Description 'helm repo add mongodb' -ScriptBlock { & helm repo add mongodb https://mongodb.github.io/helm-charts --force-update }
Invoke-Checked -Description 'helm repo add hashicorp' -ScriptBlock { & helm repo add hashicorp https://helm.releases.hashicorp.com --force-update }
Invoke-Checked -Description 'helm repo update' -ScriptBlock { & helm repo update }

Write-Host 'Installing cert-manager'
Invoke-Checked -Description 'helm upgrade --install cert-manager' -ScriptBlock {
    & helm upgrade --install cert-manager jetstack/cert-manager `
        --namespace cert-manager `
        --create-namespace `
        --set crds.enabled=true `
        --history-max 1 `
        --wait `
        --timeout 10m
}

Write-Host 'Waiting for cert-manager deployments to become ready'
Invoke-Checked -Description 'kubectl rollout status cert-manager' -ScriptBlock {
    & kubectl rollout status deployment/cert-manager --namespace cert-manager --timeout=10m
}
Invoke-Checked -Description 'kubectl rollout status cert-manager-cainjector' -ScriptBlock {
    & kubectl rollout status deployment/cert-manager-cainjector --namespace cert-manager --timeout=10m
}
Invoke-Checked -Description 'kubectl rollout status cert-manager-webhook' -ScriptBlock {
    & kubectl rollout status deployment/cert-manager-webhook --namespace cert-manager --timeout=10m
}

if (-not (Test-Path $ChartPath))
{
    throw "Helm chart path '$ChartPath' does not exist."
}
if (-not (Test-Path $ValuesFile))
{
    throw "Values file '$ValuesFile' does not exist."
}
if (-not (Test-Path $SharedCAChartPath))
{
    throw "Shared CA chart path '$SharedCAChartPath' does not exist."
}
if (-not (Test-Path $SharedCAValuesFile))
{
    throw "Shared CA values file '$SharedCAValuesFile' does not exist."
}
if (-not (Test-Path $VaultChartPath))
{
    throw "Vault chart path '$VaultChartPath' does not exist."
}
if (-not (Test-Path $VaultValuesFile))
{
    throw "Vault values file '$VaultValuesFile' does not exist."
}

Write-Host 'Updating chart dependencies'
Invoke-Checked -Description 'helm dependency update' -ScriptBlock {
    & helm dependency update $SharedCAChartPath
    & helm dependency update $VaultChartPath
    & helm dependency update $ChartPath
}

Write-Host 'Installing CloudNativePG operator'
Invoke-Checked -Description 'helm upgrade --install cnpg-operator' -ScriptBlock {
    & helm upgrade --install cnpg-operator cnpg/cloudnative-pg `
        --namespace cnpg-system `
        --create-namespace `
        --set installCRDs=true `
        --wait `
        --timeout 5m
}

Write-Host 'Installing MongoDB Community operator'
Invoke-Checked -Description 'helm upgrade --install community-operator' -ScriptBlock {
    & helm upgrade --install community-operator mongodb/community-operator `
        --namespace mongodb `
        --create-namespace `
        --set installCRDs=true `
        --set operator.watchNamespace="*" `
        --wait `
        --timeout 5m
}

function Wait-ForCRD
{
    param([Parameter(Mandatory = $true)][string]$crdName, [int]$timeoutSec = 120)

    $end = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $end)
    {
        & kubectl get crd $crdName 1> $null 2> $null
        if ($LASTEXITCODE -eq 0)
        {
            Write-Host "CRD $crdName is present"
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "CRD $crdName not found within $timeoutSec seconds"
}

function Get-RootToken
{
    if (Test-Path $VaultInitKeysFile)
    {
        return (Get-Content $VaultInitKeysFile -Raw | ConvertFrom-Json).root_token
    }

    return $null
}

function Initialize-VaultKubernetesAuth
{
    param([Parameter(Mandatory = $true)][string]$RootToken)

    $vaultPod = "$ReleaseName-vault-0"
    $policy = @'
path "secret/data/auth-service/*" {
  capabilities = ["read"]
}
'@

    Write-Host "Configuring Vault Kubernetes auth for role '$ReleaseName-auth-service'"

    Invoke-Checked -Description 'vault auth enable kubernetes' -ScriptBlock {
        & kubectl exec -n vault $vaultPod -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_TOKEN=$RootToken vault auth enable kubernetes || true"
    }

    Invoke-Checked -Description 'vault policy write auth-service' -ScriptBlock {
        $policy | & kubectl exec -i -n vault $vaultPod -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_TOKEN=$RootToken vault policy write auth-service -"
    }

    Invoke-Checked -Description 'vault kubernetes auth config' -ScriptBlock {
        & kubectl exec -n vault $vaultPod -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_TOKEN=$RootToken vault write auth/kubernetes/config kubernetes_host=https://kubernetes.default.svc:443 kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token"
    }

    Invoke-Checked -Description 'vault kubernetes auth role' -ScriptBlock {
        & kubectl exec -n vault $vaultPod -- sh -c "VAULT_ADDR=https://127.0.0.1:8200 VAULT_TOKEN=$RootToken vault write auth/kubernetes/role/$ReleaseName-auth-service bound_service_account_names=$ReleaseName-auth-service bound_service_account_namespaces=$Namespace token_policies=auth-service ttl=1h"
    }
}

function Wait-ForSecret
{
    param(
        [Parameter(Mandatory = $true)][string]$NamespaceName,
        [Parameter(Mandatory = $true)][string]$SecretName,
        [int]$timeoutSec = 180
    )

    $end = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $end)
    {
        & kubectl get secret $SecretName -n $NamespaceName 1> $null 2> $null
        if ($LASTEXITCODE -eq 0)
        {
            Write-Host "Secret $SecretName is present in namespace $NamespaceName"
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Secret $SecretName not found in namespace $NamespaceName within $timeoutSec seconds"
}

Write-Host 'Waiting for operator CRDs to be established'
try
{
    Wait-ForCRD -crdName 'clusters.postgresql.cnpg.io' -timeoutSec 120
}
catch
{
    Write-Warning "clusters.postgresql.cnpg.io not found, trying alternative CRD name"
}

try
{
    Wait-ForCRD -crdName 'mongodbcommunity.mongodbcommunity.mongodb.com' -timeoutSec 120
}
catch
{
    Write-Warning "mongodbcommunity.mongodbcommunity.mongodb.com not found, trying alternative CRD name"
}

Write-Host "Creating Vault namespace 'vault' if needed"
try
{
    & kubectl get namespace vault *> $null
    $vaultNamespaceExists = ($LASTEXITCODE -eq 0)
}
catch
{
    $vaultNamespaceExists = $false
}

if (-not $vaultNamespaceExists)
{
    Invoke-Checked -Description 'kubectl create namespace vault' -ScriptBlock {
        & kubectl create namespace vault
    }
}

Write-Host "Deploying shared CA release 'shared-ca' into namespace 'cert-manager'"
Invoke-Checked -Description 'helm upgrade --install shared-ca' -ScriptBlock {
    & helm upgrade --install shared-ca $SharedCAChartPath `
        --namespace cert-manager `
        --create-namespace `
        --values $SharedCAValuesFile `
        --history-max 3 `
        --wait `
        --timeout 10m
}
Wait-ForSecret -NamespaceName 'cert-manager' -SecretName 'time-tracking-shared-ca-tls' -timeoutSec 180

Write-Host "Deploying Vault release '$ReleaseName' into namespace 'vault'"
Invoke-Checked -Description 'helm upgrade --install vault' -ScriptBlock {
    & helm upgrade --install $ReleaseName $VaultChartPath `
        --namespace vault `
        --create-namespace `
        --values $VaultValuesFile `
        --skip-crds `
        --history-max 3 `
        --wait `
        --timeout 15m
}

$rootToken = Get-RootToken
if ($rootToken)
{
    Initialize-VaultKubernetesAuth -RootToken $rootToken
}
else
{
    Write-Warning "No '$VaultInitKeysFile' found; skipping Vault Kubernetes auth bootstrap."
}

Ensure-DockerImage -Image (Get-ImageName 'time-tracking/auth-service:latest') -ContextPath (Join-Path $repoRoot 'services\auth-service')
Ensure-DockerImage -Image (Get-ImageName 'time-tracking/project-service:latest') -ContextPath (Join-Path $repoRoot 'services\project-service')

Write-Host "Creating namespace '$Namespace' if needed"
try
{
    & kubectl get namespace $Namespace *> $null
    $namespaceExists = ($LASTEXITCODE -eq 0)
}
catch
{
    $namespaceExists = $false
}

if (-not $namespaceExists)
{
    Invoke-Checked -Description 'kubectl create namespace' -ScriptBlock {
        & kubectl create namespace $Namespace
    }
}

$secretsFile = Join-Path (Split-Path $ValuesFile) 'secrets.yaml'

Write-Host "Deploying Helm release '$ReleaseName'"
Invoke-Checked -Description 'helm upgrade --install time-tracking' -ScriptBlock {
    $helmArgs = @(
        'upgrade', '--install', $ReleaseName, $ChartPath,
        '--namespace', $Namespace,
        '--create-namespace',
        '--values', $ValuesFile,
        '--skip-crds',
        '--history-max', '3',
        '--wait',
        '--timeout', '15m'
    )
    if (Test-Path $secretsFile)
    {
        Write-Host "Applying secrets override from $secretsFile"
        $helmArgs += @('--values', $secretsFile)
    }
    & helm @helmArgs
}

Write-Host ''
Write-Host 'Bootstrap complete.'
Write-Host 'Useful follow-up commands:'
Write-Host "  kubectl -n $Namespace get pods"
Write-Host "  kubectl -n $Namespace port-forward svc/$ReleaseName-auth-service 8443:443"
Write-Host "  kubectl -n $Namespace port-forward svc/$ReleaseName-project-service 8444:443"
