<#
.SYNOPSIS
  Detiene los servicios levantados con scripts/start-local.ps1.
#>
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root '.runtime\pids.json'

if (-not (Test-Path $pidFile)) {
  Write-Host 'No hay ejecucion registrada; nada que detener.'
  return
}

$pids = Get-Content -Path $pidFile -Raw | ConvertFrom-Json
foreach ($entry in $pids.PSObject.Properties) {
  $process = Get-Process -Id $entry.Value -ErrorAction SilentlyContinue
  if ($process -and $process.ProcessName -eq 'java') {
    Stop-Process -Id $entry.Value -Force
    Write-Host "Detenido $($entry.Name) (pid $($entry.Value))"
  } else {
    Write-Host "$($entry.Name) ya no estaba en ejecucion"
  }
}
Remove-Item $pidFile
