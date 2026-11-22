#!/usr/bin/env bash
# PostgreSQL 하루 백업. 저장소 밖(BACKUP_DIR)에 남기고 14일 지난 것은 지운다.
# 덤프가 실패하면 반쯤 쓴 파일을 지우고 0 이 아닌 값으로 끝난다(cron 메일·로그로 드러나게)
set -euo pipefail

cd "$(dirname "$0")/.."
backup_dir="${BACKUP_DIR:-/var/backups/repo-inspect}"
mkdir -p "$backup_dir"
out="$backup_dir/$(date +%F).sql.gz"
tmp="$out.part"

if ! docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U repoinspect repoinspect | gzip > "$tmp"; then
  rm -f "$tmp"
  echo "백업 실패" >&2
  exit 1
fi
mv "$tmp" "$out"
find "$backup_dir" -name '*.sql.gz' -mtime +14 -delete
