# glass-auto

생성일: 2026-10-25 23:05

## 개요

(이 프로젝트가 무엇을 하는지 한두 줄로 적어주세요.)

## 두 PC 공용 규칙

이 프로젝트는 **기존 PC / 새 PC 어느 쪽에서도 똑같이** 돌아가야 합니다.

- 사용자 이름이나 특정 PC 경로를 코드에 직접 쓰지 않습니다.
  (`C:\Users\홍길동\...` 금지 → `$env:USERPROFILE`, `$env:OneDriveCommercial` 사용)
- 설치가 필요한 것은 전부 `setup.ps1` 에 넣습니다. 새 PC에서는 이것만 실행하면 됩니다.
- 실행은 `run.ps1` 하나로 끝나야 합니다.
- 비밀번호·API 키는 `.env` 에만 두고, 저장소에는 `.env.example` 만 올립니다.
  (`.env` 는 `ws save` 할 때 동기화 폴더의 `env\glass-auto\` 에 따로 보관됩니다.)
- 100MB 넘는 영상·설치파일 같은 큰 자료는 저장소에 넣지 말고
  동기화 폴더의 `assets\glass-auto\` 에 두고 여기에는 경로만 적습니다.

## 처음 받는 PC에서

```powershell
ws clone glass-auto
cd C:\work\glass-auto
.\run.ps1
```

## 매일 쓰는 명령

```powershell
ws save "무엇을 했는지"   # 중간 저장 + 다른 PC로 넘길 준비
ws pull                   # 다른 PC에서 한 작업 받아오기
ws handoff                # 작업 마무리/중단 시 인수인계 작성
```

## 필요한 것

- (예: Node.js 20 이상, Python 3.12 등 — 실제로 쓰는 것만 적으세요)

## 폴더 구조

```
glass-auto\
  setup.ps1     초기 설치 (새 PC에서 한 번)
  run.ps1       실행
  .env.example  필요한 환경변수 목록 (실제 값은 .env 에)
  README.md     이 문서
```
