[CmdletBinding()]
param(
    [string]$ClusterName = 'time-tracking-kind',
    [string]$Namespace = 'time-tracking',
    [string]$ReleaseName = 'time-tracking',
    [string]$ChartPath = '',
    [string]$ValuesFile = '',
    [switch]$RebuildImages
)

$ErrorActionPreference = 'Stop'

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found on PATH."
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$ScriptBlock,
        [Parameter(Mandatory = $true)][string]$Description
    )

    & $ScriptBlock
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Test-DockerImageExists {
    param([Parameter(Mandatory = $true)][string]$Image)

    & docker image inspect $Image *> $null
    return ($LASTEXITCODE -eq 0)
}

function Ensure-DockerImage {
    param(
        [Parameter(Mandatory = $true)][string]$Image,
        [Parameter(Mandatory = $true)][string]$ContextPath
    )

    if ($RebuildImages -or -not (Test-DockerImageExists -Image $Image)) {
        Write-Host "Building $Image from $ContextPath"
        Invoke-Checked -Description "docker build for $Image" -ScriptBlock {
            & docker build -t $Image $ContextPath
        }
    } else {
        Write-Host "Reusing existing local image $Image"
    }

    Write-Host "Loading $Image into kind cluster $ClusterName"
    Invoke-Checked -Description "kind load docker-image $Image" -ScriptBlock {
        & kind load docker-image $Image --name $ClusterName
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($ChartPath)) {
    $ChartPath = Join-Path $repoRoot 'helm\time-tracking'
}
if ([string]::IsNullOrWhiteSpace($ValuesFile)) {
    $ValuesFile = Join-Path $ChartPath 'values.yaml'
}

Assert-Command -Name 'kind'
Assert-Command -Name 'kubectl'
Assert-Command -Name 'helm'
Assert-Command -Name 'docker'

$clusterList = @(& kind get clusters)
if (-not ($clusterList -contains $ClusterName)) {
    Write-Host "Creating kind cluster '$ClusterName'"
    Invoke-Checked -Description "kind create cluster" -ScriptBlock {
        & kind create cluster --name $ClusterName
    }
} else {
    Write-Host "Kind cluster '$ClusterName' already exists"
}

Write-Host 'Adding Helm repositories'
Invoke-Checked -Description 'helm repo add jetstack' -ScriptBlock { & helm repo add jetstack https://charts.jetstack.io --force-update }
Invoke-Checked -Description 'helm repo add bitnami' -ScriptBlock { & helm repo add bitnami https://charts.bitnami.com/bitnami --force-update }
Invoke-Checked -Description 'helm repo add hashicorp' -ScriptBlock { & helm repo add hashicorp https://helm.releases.hashicorp.com --force-update }
Invoke-Checked -Description 'helm repo update' -ScriptBlock { & helm repo update }

Write-Host 'Installing cert-manager'
Invoke-Checked -Description 'helm upgrade --install cert-manager' -ScriptBlock {
    & helm upgrade --install cert-manager jetstack/cert-manager `
        --namespace cert-manager `
        --create-namespace `
        --set crds.enabled=true `
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

if (-not (Test-Path $ChartPath)) {
    throw "Helm chart path '$ChartPath' does not exist."
}
if (-not (Test-Path $ValuesFile)) {
    throw "Values file '$ValuesFile' does not exist."
}

Write-Host 'Updating chart dependencies'
Invoke-Checked -Description 'helm dependency update' -ScriptBlock {
    & helm dependency update $ChartPath
}

Ensure-DockerImage -Image 'time-tracking/auth-service:latest' -ContextPath (Join-Path $repoRoot 'services\auth-service')
Ensure-DockerImage -Image 'time-tracking/project-service:latest' -ContextPath (Join-Path $repoRoot 'services\project-service')

Write-Host "Creating namespace '$Namespace' if needed"
& kubectl get namespace $Namespace *> $null
if ($LASTEXITCODE -ne 0) {
    Invoke-Checked -Description 'kubectl create namespace' -ScriptBlock {
        & kubectl create namespace $Namespace
    }
}

Write-Host "Deploying Helm release '$ReleaseName'"
Invoke-Checked -Description 'helm upgrade --install time-tracking' -ScriptBlock {
    & helm upgrade --install $ReleaseName $ChartPath `
        --namespace $Namespace `
        --create-namespace `
        --values $ValuesFile `
        --wait `
        --timeout 15m
}

Write-Host ''
Write-Host 'Bootstrap complete.'
Write-Host 'Useful follow-up commands:'
Write-Host "  kubectl -n $Namespace get pods"
Write-Host "  kubectl -n $Namespace port-forward svc/$ReleaseName-auth-service 8443:443"
Write-Host "  kubectl -n $Namespace port-forward svc/$ReleaseName-project-service 8444:443"
