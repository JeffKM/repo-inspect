#!/usr/bin/env bash
# 테스트 순차 실행 러너: 워치독(기본 600초) + 종료 후 Gradle 데몬·테스트 컨테이너 잔존 프로세스 청소
# 사용: ./run_tests.sh [gradle 인자...]   예) ./run_tests.sh --tests '*Order*'
set -u
cd "$(dirname "$0")"

TIMEOUT="${TEST_TIMEOUT:-600}"
LOCK=".run_tests.lock"

# 같은 테스트를 동시에 두 번 돌리지 않는다
if ! mkdir "$LOCK" 2>/dev/null; then
  echo "다른 테스트가 실행 중입니다($LOCK). 끝난 뒤 다시 실행하세요." >&2
  exit 2
fi
# 실행 전부터 있던 Gradle 프로세스(다른 프로젝트)는 건드리지 않고, 이번 실행이 만든 것만 정리한다
BEFORE="$(pgrep -f 'GradleDaemon|GradleWorkerMain' | tr '\n' ' ')"
cleanup() {
  for p in $(pgrep -f 'GradleDaemon|GradleWorkerMain'); do
    case " $BEFORE " in *" $p "*) ;; *) kill -9 "$p" 2>/dev/null || true ;; esac
  done
  rmdir "$LOCK" 2>/dev/null || true
}
trap cleanup EXIT

./gradlew --no-daemon test "$@" &
pid=$!
( sleep "$TIMEOUT"; echo "워치독: ${TIMEOUT}초 초과로 강제 종료" >&2; kill -9 "$pid" 2>/dev/null ) &
wd=$!
wait "$pid"
code=$?
sl="$(pgrep -P "$wd")"              # 워치독 안의 sleep PID 를 먼저 잡는다
kill "$wd" 2>/dev/null || true      # 워치독 셸을 먼저 끝내 echo/kill -9 가 실행되지 않게
[ -n "$sl" ] && kill $sl 2>/dev/null || true
wait "$wd" 2>/dev/null || true

if [ "$code" -eq 0 ]; then
  echo "테스트 통과."
else
  echo "테스트 실패(exit $code). 리포트: build/reports/tests/test/index.html" >&2
fi
exit "$code"
