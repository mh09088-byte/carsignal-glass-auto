# glass-auto - 단위 테스트 후 release APK를 만들어 apk\ 폴더에 공모전 규칙 파일명으로 복사한다.
# 사용법: .\build-apk.ps1
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'tools\android-env.ps1')

Import-DotEnv (Join-Path $PSScriptRoot '.env')
Resolve-Keystore
Use-AndroidEnv

Write-Host "== 단위 테스트 + release 빌드" -ForegroundColor Cyan
& .\gradlew.bat --no-daemon testReleaseUnitTest assembleRelease
if ($LASTEXITCODE -ne 0) { throw "빌드 실패" }

# 공모전 제출 규칙: apk/팀명_앱이름_release.apk (개인 참가 → 팀명 = 성명)
$out = Join-Path $PSScriptRoot 'apk'
New-Item -ItemType Directory -Force $out | Out-Null
$dest = Join-Path $out '안도현_CarSignal_release.apk'
Copy-Item (Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk') $dest -Force
Write-Host "   [OK] $dest" -ForegroundColor Green
