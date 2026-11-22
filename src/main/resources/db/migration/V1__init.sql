create table repository (
  id             bigserial primary key,
  owner          varchar(100) not null,
  name           varchar(100) not null,
  default_branch varchar(255),
  stars          int,
  last_seen_at   timestamptz  not null default now(),
  unique (owner, name)
);

-- 결과 키는 (저장소, 커밋 SHA). 같은 커밋의 결과는 바뀌지 않으므로 작업도 하나만 둔다
create table analysis_job (
  id          bigserial primary key,
  repo_id     bigint       not null references repository (id),
  commit_sha  char(40)     not null,
  status      varchar(16)  not null default 'QUEUED',
  attempts    int          not null default 0,
  next_run_at timestamptz  not null default now(),
  locked_at   timestamptz,
  tool_kind   varchar(64),
  error       text,
  created_at  timestamptz  not null default now(),
  finished_at timestamptz,
  unique (repo_id, commit_sha),
  check (status in ('QUEUED', 'RUNNING', 'DONE', 'PARTIAL', 'FAILED')),
  check (attempts >= 0)
);

-- 워커는 "꺼낼 수 있는 작업"만 찾는다. 끝난 작업은 인덱스에 넣지 않는다
create index analysis_job_ready on analysis_job (next_run_at, id) where status = 'QUEUED';

create table analyzer_result (
  job_id      bigint      not null references analysis_job (id),
  analyzer    varchar(16) not null,
  status      varchar(16) not null,
  payload     jsonb,
  error       text,
  finished_at timestamptz,
  primary key (job_id, analyzer)
);

-- 권한 명세서의 근거 한 줄씩
create table finding (
  id        bigserial primary key,
  job_id    bigint      not null references analysis_job (id),
  category  varchar(32) not null,
  rule_id   varchar(64) not null,
  file_path text        not null,
  line      int         not null,
  snippet   text        not null
);

create index finding_job on finding (job_id);
