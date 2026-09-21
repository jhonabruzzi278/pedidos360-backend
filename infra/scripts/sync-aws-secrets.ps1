<#
.SYNOPSIS
  Copia las credenciales del AWS Academy Learner Lab a los secretos de GitHub Actions de ambos repos.

.DESCRIPTION
  El Learner Lab entrega credenciales temporales (access key, secret key y session token) que cambian
  cada vez que se inicia el laboratorio y caducan al terminar la sesion. Este script las lee del
  archivo ~/.aws/credentials (el bloque "AWS Details > AWS CLI: Show" del laboratorio, perfil [default])
  y las carga como secretos, enviando cada valor por entrada estandar: nunca aparece en pantalla,
  en la linea de comandos ni en ningun archivo del repositorio.

  Requisitos: GitHub CLI (gh) autenticado con permiso sobre los repos.

.EXAMPLE
  .\infra\scripts\sync-aws-secrets.ps1
  .\infra\scripts\sync-aws-secrets.ps1 -Profile default -Repos org/backend, org/frontend
#>
[CmdletBinding()]
param(
  [string]$CredentialsFile = (Join-Path $HOME '.aws\credentials'),
  [string]$Profile = 'default',
  [string[]]$Repos = @('jhonabruzzi278/pedidos360-backend', 'jhonabruzzi278/pedidos360-frontend')
)

$ErrorActionPreference = 'Stop'

function Read-CredentialProfile([string]$Path, [string]$Name) {
  if (-not (Test-Path $Path)) { throw "No existe $Path. Pegue alli el bloque de credenciales del Learner Lab." }
  $values = @{}
  $inProfile = $false
  foreach ($line in Get-Content $Path) {
    $trimmed = $line.Trim()
    if ($trimmed -match '^\[(.+)\]$') { $inProfile = ($Matches[1] -eq $Name); continue }
    if ($inProfile -and $trimmed -match '^([A-Za-z_]+)\s*=\s*(.+)$') { $values[$Matches[1]] = $Matches[2].Trim() }
  }
  return $values
}

$creds = Read-CredentialProfile -Path $CredentialsFile -Name $Profile
$secrets = [ordered]@{
  AWS_ACCESS_KEY_ID     = $creds['aws_access_key_id']
  AWS_SECRET_ACCESS_KEY = $creds['aws_secret_access_key']
  AWS_SESSION_TOKEN     = $creds['aws_session_token']
}

foreach ($name in $secrets.Keys) {
  if ([string]::IsNullOrWhiteSpace($secrets[$name])) {
    throw "Falta $($name.ToLower()) en el perfil [$Profile] de $CredentialsFile. Las credenciales del Learner Lab incluyen aws_session_token."
  }
}
if ($secrets['AWS_ACCESS_KEY_ID'] -notmatch '^ASIA') {
  Write-Warning 'La access key no empieza con ASIA: no parece una credencial temporal del Learner Lab.'
}

foreach ($repo in $Repos) {
  foreach ($name in $secrets.Keys) {
    $secrets[$name] | gh secret set $name --repo $repo
    if ($LASTEXITCODE -ne 0) { throw "No se pudo cargar $name en $repo (gh salio con codigo $LASTEXITCODE)." }
    Write-Host "OK  $repo  $name"
  }
}

Write-Host ''
Write-Host 'Listo. Las credenciales caducan cuando termina la sesion del laboratorio: vuelva a ejecutar este script tras cada inicio.'
