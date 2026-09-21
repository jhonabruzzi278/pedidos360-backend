<#
.SYNOPSIS
  Compila y levanta orders-service (8081), audit-service (8082) y el BFF (8080, perfil local).
.DESCRIPTION
  SOLO desarrollo. Activa el perfil "local" del BFF, que emite y acepta tokens firmados con un
  secreto conocido. Los logs y los pids quedan en .runtime/ (ignorado por Git).
  Detener con scripts/stop-local.ps1.
#>
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root '.runtime'
$pidFile = Join-Path $runtime 'pids.json'
$timeoutSeconds = 180

$services = @(
  [pscustomobject]@{ Name = 'orders-service'; Port = 8081; Profile = $null },
  [pscustomobject]@{ Name = 'audit-service';  Port = 8082; Profile = $null },
  [pscustomobject]@{ Name = 'bff';            Port = 8080; Profile = 'local' }
)

# 127.0.0.1 y no "localhost": el BFF escucha solo en IPv4 y en Windows un intento a ::1 (puerto cerrado)
# tarda ~2 s en fallar, lo que agotaba por completo el timeout de la consulta.
function Test-Health([int] $port) {
  try {
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/actuator/health" -UseBasicParsing -TimeoutSec 5
    return $response.StatusCode -eq 200
  } catch { return $false }
}

if (Test-Path $pidFile) {
  throw "Ya hay una ejecucion registrada en $pidFile. Ejecuta primero scripts/stop-local.ps1."
}
foreach ($service in $services) {
  if (Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue) {
    throw "El puerto $($service.Port) ($($service.Name)) ya esta en uso."
  }
}

New-Item -ItemType Directory -Force -Path $runtime | Out-Null

Write-Host 'Compilando (sin pruebas)...'
& (Join-Path $root 'mvnw.cmd') -f (Join-Path $root 'pom.xml') -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "La compilacion fallo (codigo $LASTEXITCODE)." }

$started = [ordered]@{}
try {
  foreach ($service in $services) {
    $jar = Join-Path $root "$($service.Name)\target\$($service.Name)-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "No existe $jar" }
    if ($service.Profile) { $env:SPRING_PROFILES_ACTIVE = $service.Profile }
    try {
      $process = Start-Process -FilePath 'java' -ArgumentList '-jar', $jar -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtime "$($service.Name).log") `
        -RedirectStandardError (Join-Path $runtime "$($service.Name).err.log")
    } finally { Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue }
    $started[$service.Name] = $process.Id
  }
  $started | ConvertTo-Json | Set-Content -Path $pidFile -Encoding utf8

  foreach ($service in $services) {
    # Plazo propio por servicio: los tres arrancan a la vez y el arranque de uno no debe consumir el de otro.
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while (-not (Test-Health $service.Port)) {
      if ((Get-Date) -gt $deadline) { throw "$($service.Name) no respondio en $timeoutSeconds s. Revisa .runtime/$($service.Name).log" }
      Start-Sleep -Seconds 2
    }
    Write-Host "OK  $($service.Name) en http://127.0.0.1:$($service.Port)"
  }
} catch {
  foreach ($id in $started.Values) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
  Remove-Item $pidFile -ErrorAction SilentlyContinue
  throw
}

Write-Host "`nListo. BFF en http://127.0.0.1:8080 (perfil local, solo accesible desde este equipo)."
Write-Host 'Para detener: scripts/stop-local.ps1'
