$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!(Test-Path './neoms.pid')) { Write-Host 'No background PID file; use Ctrl+C for a foreground instance.'; exit }
$neomsPid = [int](Get-Content './neoms.pid')
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$neomsPid"
if ($process) {
  if ($process.Name -notmatch '^java(w)?\.exe$' -or $process.CommandLine -notmatch 'neoms\.jar') { throw 'PID does not belong to NEOMS. Refusing to stop.' }
  Stop-Process -Id $neomsPid
}
Remove-Item -LiteralPath './neoms.pid'
Write-Host 'NEOMS stopped.'
