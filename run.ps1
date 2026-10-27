# glass-auto - 연결된 Pleos Connect 에뮬레이터(또는 Android 기기)에 APK를 설치하고 실행한다.
# 먼저 Android Studio Device Manager 에서 Pleos Connect AVD 를 실행해 두세요.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'tools\android-env.ps1')
Use-AndroidEnv

$apk = Join-Path $PSScriptRoot 'apk\안도현_CarSignal_release.apk'
if (-not (Test-Path $apk)) { & (Join-Path $PSScriptRoot 'build-apk.ps1') }

$devices = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $devices) { throw "연결된 에뮬레이터/기기가 없습니다. Pleos Connect AVD 를 먼저 실행하세요." }

& adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "APK 설치 실패. 에뮬레이터가 완전히 켜졌는지 확인한 뒤 다시 실행하세요." }

# 신호등 타임 실데이터: .env 의 공공 신호 API 키를 실행 인자로 한 번 넘기면 앱이 자기 전용 저장소에 보관한다.
# (APK에는 키를 넣지 않음. Pleos 는 사용자 10 으로 동작해 adb 가 앱 폴더에 파일을 넣을 수 없음)
Import-DotEnv (Join-Path $PSScriptRoot '.env')
$extras = @()
foreach ($k in 'DATA_GO_KR_SERVICE_KEY', 'TDATA_API_KEY') {
    $v = [Environment]::GetEnvironmentVariable($k)
    if ($v) { $extras += @('--es', $k, $v) }
}
& adb shell am start -S -n io.github.glassauto/.ui.MainActivity @extras | Out-Null
if ($LASTEXITCODE -ne 0) { throw "앱 실행 실패" }
if ($extras) { Write-Host "   [OK] 실행 완료 · 공공 신호 API 키 전달" -ForegroundColor Green } else { Write-Host "   [OK] 실행 완료 · 신호 API 키 없음(시나리오 모드)" -ForegroundColor Green }
