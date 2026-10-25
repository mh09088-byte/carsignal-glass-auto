# 공통: JDK 17 / Android SDK 위치를 찾고 환경변수를 잡는다. (setup.ps1, build-apk.ps1, run.ps1 에서 사용)
# 경로를 직접 쓰지 않고 사용자 폴더 기준으로 찾는다.

function Find-Jdk17 {
    $cands = @()
    if ($env:JAVA_HOME) { $cands += $env:JAVA_HOME }
    $cands += Get-ChildItem "$env:LOCALAPPDATA\Programs\Microsoft", "$env:ProgramFiles\Microsoft" -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName }
    foreach ($c in $cands) { if ($c -and (Test-Path (Join-Path $c 'bin\java.exe'))) { return $c } }
    return $null
}

function Get-AndroidSdkRoot {
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
    return (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
}

function Use-AndroidEnv {
    $jdk = Find-Jdk17
    if (-not $jdk) { throw "JDK 17 이 없습니다. setup.ps1 을 먼저 실행하세요." }
    $env:JAVA_HOME = $jdk
    $env:ANDROID_HOME = Get-AndroidSdkRoot
    $env:Path = "$jdk\bin;$($env:ANDROID_HOME)\platform-tools;$env:Path"
    # Gradle 이 읽는 local.properties (PC마다 다르므로 커밋하지 않음)
    $sdk = $env:ANDROID_HOME -replace '\\', '\\' -replace ':', '\:'
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot '..\local.properties'), "sdk.dir=$sdk`n", (New-Object System.Text.UTF8Encoding($false)))
}

function Import-DotEnv([string]$path) {
    if (-not (Test-Path $path)) { return }
    Get-Content $path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $i = $line.IndexOf('=')
            [Environment]::SetEnvironmentVariable($line.Substring(0, $i).Trim(), $line.Substring($i + 1).Trim(), 'Process')
        }
    }
}

# 서명 키: .env 의 경로가 비어 있으면 동기화 폴더(CLAUDE-SYNC\env\glass-auto\release.jks)에서 찾는다.
function Resolve-Keystore {
    if ($env:GLASSAUTO_KEYSTORE -and (Test-Path $env:GLASSAUTO_KEYSTORE)) { return }
    $marker = Join-Path $env:USERPROFILE '.ws-sync-root'
    if (Test-Path $marker) {
        $cand = Join-Path ((Get-Content $marker -Raw).Trim()) 'env\glass-auto\release.jks'
        if (Test-Path $cand) { $env:GLASSAUTO_KEYSTORE = $cand; return }
    }
    Write-Host "   [!] release 서명 키를 찾지 못해 debug 키로 서명합니다." -ForegroundColor Yellow
}
