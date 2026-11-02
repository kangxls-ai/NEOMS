param([switch]$Background)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
if (!(Test-Path -LiteralPath './neoms.jar')) { throw 'neoms.jar missing. Run build.ps1 first or use the release directory.' }
if (!(Test-Path -LiteralPath './lib')) { throw 'External lib directory missing.' }
$version = (& $java -version 2>&1 | Out-String)
if ($version -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21) { throw 'Java 21 or later is required.' }
New-Item -ItemType Directory -Force logs,data | Out-Null
# Use a short real Windows directory for the JDK internal NIO socket path.
$tempDir = if ($env:NEOMS_JAVA_TMP) { $env:NEOMS_JAVA_TMP } else { (Join-Path $env:SystemRoot 'Temp').Replace('\','/') }
$argsList = @("`"-Djdk.net.unixdomain.tmpdir=$tempDir`"",'"-Dloader.config.location=file:./config/loader.properties"','-jar','neoms.jar')
if ($Background) {
  if (Test-Path './neoms.pid') { throw 'PID file exists. Check the running instance before starting again.' }
  $proc = Start-Process -FilePath $java -ArgumentList $argsList -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput './logs/console.log' -RedirectStandardError './logs/console-error.log' -PassThru
  $proc.Id | Set-Content './neoms.pid'
  $port = if ($env:NEOMS_PORT) {$env:NEOMS_PORT} else {'8080'}
  $ready=$false
  for ($i=0;$i -lt 50;$i++) {
    Start-Sleep -Milliseconds 400
    $proc.Refresh()
    if ($proc.HasExited) { Remove-Item -LiteralPath './neoms.pid'; throw 'NEOMS startup failed. See logs/console.log and logs/neoms.log.' }
    try { $health=Invoke-RestMethod "http://127.0.0.1:$port/api/health" -TimeoutSec 1; if($health.status -eq 'UP'){$ready=$true;break} } catch {}
  }
  if($ready){Write-Host "NEOMS ready (PID $($proc.Id)): http://localhost:$port"}else{Write-Host "NEOMS process started (PID $($proc.Id)). Check configured port and logs/neoms.log."}
} else {
  Write-Host 'NEOMS: http://localhost:8080 ; Ctrl+C to stop.'
  & $java "-Djdk.net.unixdomain.tmpdir=$tempDir" '-Dloader.config.location=file:./config/loader.properties' -jar neoms.jar
  exit $LASTEXITCODE
}
