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
& adb shell am start -n io.github.glassauto/.ui.MainActivity
