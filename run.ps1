# glass-auto - 실행 스크립트
# 두 PC 어디서든 이 파일 하나만 실행하면 되도록 유지하세요.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# .env 를 환경변수로 읽어들이기
if (Test-Path '.env') {
    Get-Content '.env' -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and (-not $line.StartsWith('#')) -and $line.Contains('=')) {
            $i = $line.IndexOf('=')
            $k = $line.Substring(0, $i).Trim()
            $v = $line.Substring($i + 1).Trim()
            [Environment]::SetEnvironmentVariable($k, $v, 'Process')
        }
    }
}

# --- 여기부터 프로젝트에 맞게 고치세요 ---------------------------------

if (Test-Path 'package.json') {
    $pkg = Get-Content 'package.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($pkg.scripts -and $pkg.scripts.start) {
        & npm run start
        exit $LASTEXITCODE
    }
    if ($pkg.scripts -and $pkg.scripts.dev) {
        & npm run dev
        exit $LASTEXITCODE
    }
}

$py = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path $py)) { $py = 'python' }
foreach ($entry in @('main.py', 'app.py', 'run.py')) {
    if (Test-Path $entry) {
        & $py $entry
        exit $LASTEXITCODE
    }
}

Write-Host ""
Write-Host "  아직 실행 방법이 정해지지 않았습니다." -ForegroundColor Yellow
Write-Host "  run.ps1 아래쪽에 실제 실행 명령을 적어주세요." -ForegroundColor Yellow
Write-Host ""
