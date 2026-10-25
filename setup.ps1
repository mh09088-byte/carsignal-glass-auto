# glass-auto - 새 PC에서 한 번만 실행하면 되는 초기 설치 스크립트
# 어느 PC에서 실행해도 같은 결과가 나오도록, 경로를 직접 쓰지 않고 스크립트 위치 기준으로 동작합니다.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

Write-Host ""
Write-Host "== glass-auto 초기 설치" -ForegroundColor Cyan
Write-Host "   위치: $PSScriptRoot"

# 1) 비밀값 파일 준비
if ((Test-Path '.env.example') -and (-not (Test-Path '.env'))) {
    Copy-Item '.env.example' '.env'
    Write-Host "   [!] .env 를 새로 만들었습니다. 실제 값을 채워주세요." -ForegroundColor Yellow
}

# 2) Node.js 프로젝트
if (Test-Path 'package.json') {
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        throw "Node.js/npm 이 설치되어 있지 않습니다. https://nodejs.org 에서 LTS 설치 후 다시 실행하세요."
    }
    Write-Host "   Node 의존성 설치 중..." -ForegroundColor Gray
    if (Test-Path 'package-lock.json') { & npm ci } else { & npm install }
    if ($LASTEXITCODE -ne 0) { throw "npm 설치 실패" }
    Write-Host "   [OK] Node 의존성 설치 완료" -ForegroundColor Green
}

# 3) Python 프로젝트
if ((Test-Path 'requirements.txt') -or (Test-Path 'pyproject.toml')) {
    if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
        throw "Python 이 설치되어 있지 않습니다. https://www.python.org 에서 설치 후 다시 실행하세요."
    }
    if (-not (Test-Path '.venv')) {
        Write-Host "   가상환경(.venv) 생성 중..." -ForegroundColor Gray
        & python -m venv .venv
        if ($LASTEXITCODE -ne 0) { throw "가상환경 생성 실패" }
    }
    $py = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
    & $py -m pip install --upgrade pip --quiet
    if (Test-Path 'requirements.txt') {
        Write-Host "   Python 의존성 설치 중..." -ForegroundColor Gray
        & $py -m pip install -r requirements.txt
        if ($LASTEXITCODE -ne 0) { throw "pip 설치 실패" }
    }
    Write-Host "   [OK] Python 환경 준비 완료" -ForegroundColor Green
}

Write-Host ""
Write-Host "   설치 완료. 실행하려면:  .\run.ps1" -ForegroundColor Green
Write-Host ""
