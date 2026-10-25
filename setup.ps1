# glass-auto - 새 PC에서 한 번만 실행하면 되는 초기 설치 스크립트
# JDK 17, Android SDK(명령줄 도구 + Android 14 플랫폼 + 빌드 도구)를 사용자 폴더에 설치한다.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'tools\android-env.ps1')

Write-Host ""
Write-Host "== glass-auto 초기 설치" -ForegroundColor Cyan
Write-Host "   위치: $PSScriptRoot"

# 1) 비밀값 파일 준비 (동기화 폴더에 사본이 있으면 ws pull / ws clone 이 복원한다)
if ((Test-Path '.env.example') -and (-not (Test-Path '.env'))) {
    Copy-Item '.env.example' '.env'
    Write-Host "   [!] .env 를 새로 만들었습니다. 서명 키 비밀번호를 채우거나 'ws pull' 로 복원하세요." -ForegroundColor Yellow
}

# 2) JDK 17 (Microsoft OpenJDK)
if (-not (Find-Jdk17)) {
    Write-Host "   JDK 17 설치 중 (winget)..." -ForegroundColor Gray
    winget install --id Microsoft.OpenJDK.17 -e --silent --scope user --accept-package-agreements --accept-source-agreements
    if (-not (Find-Jdk17)) { throw "JDK 17 설치를 확인하지 못했습니다." }
}
Write-Host "   [OK] JDK 17: $(Find-Jdk17)" -ForegroundColor Green

# 3) Android SDK 명령줄 도구 (Google 공식, SHA-256 검증)
$sdk = Get-AndroidSdkRoot
$sm = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sm)) {
    $ver = '15859902'
    $sha = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    $zip = Join-Path $env:TEMP "commandlinetools-win-$ver.zip"
    Write-Host "   Android 명령줄 도구 다운로드 중..." -ForegroundColor Gray
    Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-${ver}_latest.zip" -OutFile $zip -UseBasicParsing
    if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $sha) { throw "명령줄 도구 체크섬 불일치" }
    $tmp = Join-Path $env:TEMP 'glassauto-cmdline'
    Expand-Archive $zip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
}

# 4) SDK 구성요소 (라이선스 동의 포함)
Use-AndroidEnv
$yes = ("y`n" * 40)
$yes | & $sm --sdk_root=$sdk --licenses | Out-Null
$yes | & $sm --sdk_root=$sdk 'platforms;android-34' 'build-tools;34.0.0' 'platform-tools' | Out-Null
Write-Host "   [OK] Android SDK: $sdk" -ForegroundColor Green

Write-Host ""
Write-Host "   설치 완료. APK 빌드:  .\build-apk.ps1   /   기기·에뮬레이터 실행:  .\run.ps1" -ForegroundColor Green
Write-Host ""
