param([string]$Maven='mvn')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
Push-Location frontend
try { & npm.cmd ci; if ($LASTEXITCODE) {throw 'npm ci failed'}; & npm.cmd run build; if ($LASTEXITCODE) {throw 'Frontend build failed'} } finally { Pop-Location }
& $Maven '-Dmaven.repo.local=./.cache/m2' clean package
if ($LASTEXITCODE) {throw 'Maven build/tests failed'}
$release=Join-Path $PSScriptRoot 'release/NEOMS-0.2.0'
if (Test-Path -LiteralPath $release) { throw 'Release directory already exists. Rename it before rebuilding to avoid mixing old/new files.' }
New-Item -ItemType Directory -Force "$release/config/static","$release/lib","$release/docs" | Out-Null
Copy-Item target/neoms.jar $release
Copy-Item target/lib/* "$release/lib"
Copy-Item frontend/dist/* "$release/config/static" -Recurse
Copy-Item config/application.yml,config/loader.properties,config/application-local.example.yml "$release/config"
Copy-Item start.ps1,start.cmd,stop.ps1,start.sh,stop.sh,start-macos.command,README.md $release
Copy-Item docs/* "$release/docs" -Recurse
Get-ChildItem "$release/lib" -File | ForEach-Object { "$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower())  lib/$($_.Name)" } | Set-Content "$release/lib-manifest.sha256"
Compress-Archive -Path $release -DestinationPath 'release/NEOMS-0.2.0.zip'
Write-Host "Release ready: $release"
