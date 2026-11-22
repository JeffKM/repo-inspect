-- 실패한 작업을 다시 돌릴 때 attempts 는 펜싱 값이라 되돌리지 않는다. 이번 회차가 시작된 attempts 값을 따로 둔다
alter table analysis_job add column attempt_base int not null default 0;
alter table analysis_job add constraint analysis_job_attempt_base check (attempt_base between 0 and attempts);
