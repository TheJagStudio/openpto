# OpenPTO local dev runner.
#   .\dev.ps1 up        start Postgres + all 4 services + Angular dev server (each in its own window)
#   .\dev.ps1 down      stop everything started by `up` (only those PIDs) and Postgres
#   .\dev.ps1 build     build + test every service and the web app
#   .\dev.ps1 status    show what is listening
param([Parameter(Position=0)][ValidateSet('up','down','build','status')][string]$Cmd = 'status')
$ErrorActionPreference = 'Stop'
$root  = $PSScriptRoot
$tools = 'D:\JavaSpring\tools'
. "$tools\env.ps1" | Out-Null
$pidFile = Join-Path $root 'data\dev-pids.txt'
New-Item -ItemType Directory -Force (Join-Path $root 'data') | Out-Null

# Order matters: odp first (JWKS + internal APIs), gateway last.
$services = @(
  @{ name='odp-service';    port=8081; profile='dev' },
  @{ name='fee-service';    port=8082; profile='dev' },
  @{ name='ingest-service'; port=8083; profile='dev' },
  @{ name='gateway';        port=8080; profile='dev' }
)

function Wait-Port($port, $seconds = 120) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt $seconds) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { return $true }
    Start-Sleep -Milliseconds 700
  }
  return $false
}

switch ($Cmd) {
  'up' {
    & "$tools\pg.ps1" start
    & "$tools\pg.ps1" createdb openpto openpto openpto
    $pids = @()
    foreach ($s in $services) {
      $dir = Join-Path $root $s.name
      $cmdline = ". '$tools\env.ps1'; `$host.UI.RawUI.WindowTitle='$($s.name) :$($s.port)'; " +
                 "Set-Location '$dir'; .\gradlew.bat bootRun --args='--spring.profiles.active=$($s.profile)'"
      $p = Start-Process powershell -ArgumentList '-NoExit','-Command',$cmdline -PassThru
      $pids += $p.Id
      Write-Host "starting $($s.name) on :$($s.port) (pid $($p.Id))"
      if (-not (Wait-Port $s.port 180)) { Write-Warning "$($s.name) did not open :$($s.port) in time — check its window" }
    }
    $web = Join-Path $root 'web'
    $p = Start-Process powershell -ArgumentList '-NoExit','-Command',". '$tools\env.ps1'; `$host.UI.RawUI.WindowTitle='web :4200'; Set-Location '$web'; npm start" -PassThru
    $pids += $p.Id
    $pids | Set-Content $pidFile
    Wait-Port 4200 180 | Out-Null
    Write-Host ""
    Write-Host "Web app      http://localhost:4200"
    Write-Host "Gateway      http://localhost:8080   (API docs: http://localhost:8080/swagger-ui.html)"
    Write-Host "Admin login  admin@openpto.local / Admin#12345 (dev only)"
  }
  'down' {
    if (Test-Path $pidFile) {
      foreach ($id in Get-Content $pidFile) {
        # kill the window process tree we started (gradle bootRun forks a java child)
        taskkill.exe /PID $id /T /F 2>$null | Out-Null
      }
      Remove-Item $pidFile
    }
    & "$tools\pg.ps1" stop
  }
  'build' {
    foreach ($s in $services) {
      Write-Host "== $($s.name)"; Push-Location (Join-Path $root $s.name)
      .\gradlew.bat build; if ($LASTEXITCODE) { Pop-Location; throw "$($s.name) build failed" }
      Pop-Location
    }
    Write-Host "== web"; Push-Location (Join-Path $root 'web')
    npm ci; npm run build; npm test -- --watch=false
    if ($LASTEXITCODE) { Pop-Location; throw 'web build failed' }
    Pop-Location
  }
  'status' {
    foreach ($port in 5433, 8081, 8082, 8083, 8080, 4200) {
      $up = [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
      "{0,-6} {1}" -f $port, ($(if ($up) { 'UP' } else { '-' }))
    }
  }
}
