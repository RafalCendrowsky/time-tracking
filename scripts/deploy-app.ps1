[CmdletBinding()]
param(
    [string]$ClusterName = 'time-tracking-kind',
    [string]$Namespace = 'time-tracking',
    [string]$ReleaseName = 'time-tracking',
    [string]$ChartPath = '',
    [string]$ValuesFile = '',
    [string]$Registry = '',
    [switch]$BuildImages,
    [switch]$SkipImages
)

# Small convenience script to deploy only the application (auth/project) into an existing cluster
# - Does NOT install operators, cert-manager, the shared CA chart, or Vault
# - Expects the shared CA and Vault to already be installed (Vault in the dedicated `vault` namespace)
# - By default will build & load local images for the two services; pass -SkipImages to skip that
# - Use -BuildImages to force rebuild even if images exist locally

$ErrorActionPreference = 'Stop'

function Assert-Command { param([string]$Name) if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "Required command '$Name' was not found on PATH." } }

function Get-ImageName { param([string]$Repository) if ([string]::IsNullOrWhiteSpace($Registry)) { return $Repository } return ($Registry.TrimEnd('/') + '/' + $Repository) }

function Test-DockerImageExists { param([string]$Image) & docker image inspect $Image 1>$null 2>$null; return ($LASTEXITCODE -eq 0) }

function Ensure-DockerImage {
    param([string]$Image, [string]$ContextPath)
    if ($BuildImages -or -not (Test-DockerImageExists -Image $Image)) {
        Write-Host "Building $Image from $ContextPath"
        & docker build -t $Image $ContextPath
        if ($LASTEXITCODE -ne 0) { throw "docker build failed for $Image" }
    } else {
        Write-Host "Reusing existing local image $Image"
    }

    $archivePath = [System.IO.Path]::Combine($env:TEMP, ([System.Guid]::NewGuid().ToString() + '.tar'))
    try {
        Write-Host "Saving $Image to $archivePath"
        & docker save -o $archivePath $Image
        if ($LASTEXITCODE -ne 0) { throw "docker save failed for $Image" }

        Write-Host "Loading $Image archive into kind cluster $ClusterName"
        & kind load image-archive $archivePath --name $ClusterName
        if ($LASTEXITCODE -ne 0) { throw "kind load image-archive failed for $Image" }
    } finally {
        if (Test-Path $archivePath) { Remove-Item $archivePath -Force -ErrorAction SilentlyContinue }
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($ChartPath)) { $ChartPath = Join-Path $repoRoot 'helm\time-tracking' }
if ([string]::IsNullOrWhiteSpace($ValuesFile)) { $ValuesFile = Join-Path $ChartPath 'values.yaml' }

Assert-Command kind; Assert-Command kubectl; Assert-Command helm; Assert-Command docker

if (-not (Test-Path $ChartPath)) { throw "Helm chart path '$ChartPath' does not exist." }
if (-not (Test-Path $ValuesFile)) { throw "Values file '$ValuesFile' does not exist." }

# Build/load images for local services unless user asked to skip
if (-not $SkipImages) {
    Ensure-DockerImage -Image (Get-ImageName 'time-tracking/auth-service:latest') -ContextPath (Join-Path $repoRoot 'services\auth-service')
    Ensure-DockerImage -Image (Get-ImageName 'time-tracking/project-service:latest') -ContextPath (Join-Path $repoRoot 'services\project-service')
} else {
    Write-Host 'Skipping image build/load as requested (-SkipImages)'
}

# Ensure namespace exists
try { & kubectl get namespace $Namespace 1>$null 2>$null; $nsExists = ($LASTEXITCODE -eq 0) } catch { $nsExists = $false }
if (-not $nsExists) { & kubectl create namespace $Namespace ; if ($LASTEXITCODE -ne 0) { throw "failed to create namespace $Namespace" } }

# Deploy umbrella chart but skip CRDs (operators must already be installed)
$helmArgs = @(
    'upgrade','--install',$ReleaseName,$ChartPath,
    '--namespace',$Namespace,
    '--create-namespace',
    '--values',$ValuesFile,
    '--skip-crds',
    '--history-max','3',
    '--wait',
    '--timeout','15m'
)

Write-Host "Deploying application release '$ReleaseName' (skip CRDs)"
& helm @helmArgs
if ($LASTEXITCODE -ne 0) { throw 'helm upgrade/install failed' }

Write-Host 'Application deployment complete.'
Write-Host "Run: kubectl -n $Namespace get pods"
