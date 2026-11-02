#!/usr/bin/env bash
set -euo pipefail
cd -- "$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
( cd frontend && npm ci && npm run build )
"${MAVEN:-mvn}" -Dmaven.repo.local=./.cache/m2 clean package
release=release/NEOMS-0.2.0
[[ ! -e "$release" ]] || { echo 'Release directory exists. Rename it before rebuilding.'; exit 1; }
mkdir -p "$release/config/static" "$release/lib" "$release/docs"
cp target/neoms.jar README.md start.ps1 start.cmd stop.ps1 start.sh stop.sh start-macos.command "$release/"
cp target/lib/* "$release/lib/"
cp -R frontend/dist/. "$release/config/static/"
cp config/application.yml config/loader.properties config/application-local.example.yml "$release/config/"
cp -R docs/. "$release/docs/"
chmod +x "$release/"*.sh "$release/start-macos.command"
( cd "$release"; if command -v sha256sum >/dev/null; then sha256sum lib/*.jar >lib-manifest.sha256; else shasum -a 256 lib/*.jar >lib-manifest.sha256; fi )
tar -czf release/NEOMS-0.2.0.tar.gz -C release NEOMS-0.2.0
echo "Release ready: $release"
