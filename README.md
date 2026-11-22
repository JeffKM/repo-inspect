# repo-inspect (가칭)

AI 도구 저장소(skills, hooks, MCP 서버, 플러그인)를 설치하기 전에, GitHub 링크 하나로 그 도구가 어떤 권한을 쓰는지 근거 줄과 함께 보여 주는 서비스입니다.

## 무엇을 보여 주나

- **권한 명세서:** 셸 실행, 파일 접근, 외부 전송, 환경 변수·비밀, 설치 스크립트, 난독화 여섯 범주로 나눠 파일 이름, 줄 번호, 해당 줄을 보여 줍니다.
- **주요/참고 구분:** 본 코드·에이전트 지시문·설정에서 나온 근거는 주요, 테스트·예제·문서 폴더에서 나온 근거는 참고로 나눕니다. 참고 근거도 지우지 않습니다.
- **외부 도메인 목록:** 코드와 지시문이 부르는 주소의 도메인을 모읍니다.
- **도구 종류:** SKILL.md, hooks, MCP 서버 의존성, 플러그인 매니페스트로 종류를 판별합니다.

결과는 "발견된 신호"입니다. 신호가 없다고 안전하다는 뜻은 아닙니다.

## 어떻게 동작하나

- 저장소 코드를 실행하지 않습니다. 기본 브랜치의 최신 커밋 압축 파일을 디스크에 풀지 않고 스트림으로 읽어 줄 단위 규칙으로 훑습니다.
- 결과는 `(저장소, 커밋 SHA)` 단위로 한 번만 만듭니다. 같은 커밋을 다시 요청하면 저장된 결과를 돌려줍니다.
- 분석 작업은 PostgreSQL 대기열(`FOR UPDATE SKIP LOCKED`)로 처리하고, 시도 횟수로 펜싱해 늦게 끝난 워커가 결과를 덮어쓰지 못하게 합니다.
- 압축 폭탄, 경로 탈출, 링크 항목, 느리거나 멈춘 응답, GitHub 한도, 같은 IP 의 요청 폭주를 막습니다.

설계와 구현 차이는 [docs/01-MVP-설계.md](docs/01-MVP-설계.md), 배포 절차는 [docs/02-배포.md](docs/02-배포.md) 에 있습니다.

## API

| 요청 | 설명 |
|---|---|
| `POST /api/analyses` `{"url": "https://github.com/owner/repo"}` | 분석 요청. 202 와 작업 번호 |
| `GET /api/analyses/{id}` | 진행 상태와 결과 |

모든 응답은 `{"success": true|false, "data": ..., "error": ...}` 형태입니다.

| 상태 | 언제 |
|---|---|
| 400 | github.com 저장소 주소가 아님 |
| 404 | 없는 저장소(공개 저장소만 분석) 또는 없는 분석 |
| 409 | 커밋이 없는 빈 저장소 |
| 429 | 같은 IP 의 분석 요청이 1분 상한을 넘음(`Retry-After`) |
| 502 | GitHub 응답 오류 |
| 503 | GitHub 한도 초과(`Retry-After`) |

## 로컬 실행

필요한 것: JDK 17, Docker.

```bash
docker compose up -d postgres redis
GITHUB_TOKEN=<공개 저장소 읽기 토큰> ./gradlew bootRun
curl -s -X POST localhost:8080/api/analyses -H 'Content-Type: application/json' \
  -d '{"url": "https://github.com/anthropics/skills"}'
```

토큰 없이도 돌지만 GitHub 비인증 한도(시간당 60회)에 금방 걸립니다.

## 테스트

```bash
./run_tests.sh
```

Testcontainers 로 PostgreSQL 과 Redis 를 띄우므로 Docker 가 실행 중이어야 합니다.
