package com.jeffkm.repoinspect.job;

import com.jeffkm.repoinspect.github.RepoRef;
import com.jeffkm.repoinspect.permission.Finding;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 작업 대기열과 결과. 대기열은 PostgreSQL FOR UPDATE SKIP LOCKED 로 만든다(워커가 여러 개여도 한 작업은 한 워커만). */
@Repository
public class JobRepository {

  private final JdbcClient jdbc;
  private final JdbcTemplate template;

  public JobRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  public long upsertRepository(RepoRef ref) {
    return jdbc.sql("""
            insert into repository (owner, name) values (:owner, :name)
            on conflict (owner, name) do update set last_seen_at = now()
            returning id
            """)
        .param("owner", ref.owner())
        .param("name", ref.name())
        .query(Long.class)
        .single();
  }

  /** 같은 커밋의 결과는 바뀌지 않으므로 작업은 하나만. 이미 있으면 그 작업 id 를 돌려준다. */
  public long enqueue(long repoId, String sha) {
    Optional<Long> created = jdbc.sql("""
            insert into analysis_job (repo_id, commit_sha) values (:repoId, :sha)
            on conflict (repo_id, commit_sha) do nothing
            returning id
            """)
        .param("repoId", repoId)
        .param("sha", sha)
        .query(Long.class)
        .optional();
    return created.orElseGet(() -> jdbc.sql("select id from analysis_job where repo_id = :repoId and commit_sha = :sha")
        .param("repoId", repoId)
        .param("sha", sha)
        .query(Long.class)
        .single());
  }

  public Optional<ClaimedJob> claimNext(Instant now) {
    return jdbc.sql("""
            with picked as (
              select id from analysis_job
              where status = 'QUEUED' and next_run_at <= :now
              order by next_run_at, id
              limit 1
              for update skip locked
            ), upd as (
              update analysis_job j
              set status = 'RUNNING', locked_at = :now, attempts = j.attempts + 1
              from picked
              where j.id = picked.id
              returning j.id, j.repo_id, j.commit_sha, j.attempts, j.attempt_base
            )
            select upd.id, upd.commit_sha, upd.attempts, upd.attempts - upd.attempt_base as round_attempts, r.owner, r.name
            from upd join repository r on r.id = upd.repo_id
            """)
        .param("now", ts(now))
        .query((rs, n) -> new ClaimedJob(rs.getLong("id"), new RepoRef(rs.getString("owner"), rs.getString("name")),
            rs.getString("commit_sha"), rs.getInt("attempts"), rs.getInt("round_attempts")))
        .optional();
  }

  // 펜싱: claim 마다 attempts 가 오르고, 한도 대기 reschedule 은 claim 전 값만 되돌리므로 이전 claim 의 값은 다시 RUNNING 이 될 수 없다
  /** countAttempt=false: GitHub 한도처럼 우리 잘못이 아닌 대기는 시도 횟수에서 뺀다. 내 claim 이 아니면 false. */
  public boolean reschedule(long jobId, int claimedAttempts, Instant nextRunAt, boolean countAttempt) {
    return jdbc.sql("""
            update analysis_job
            set status = 'QUEUED', locked_at = null, next_run_at = :next,
                attempts = case when :countAttempt then attempts else greatest(attempts - 1, 0) end
            where id = :id and status = 'RUNNING' and attempts = :claimed
            """)
        .param("id", jobId)
        .param("claimed", claimedAttempts)
        .param("next", ts(nextRunAt))
        .param("countAttempt", countAttempt)
        .update() == 1;
  }

  public boolean fail(long jobId, int claimedAttempts, String error, Instant now) {
    return jdbc.sql("""
            update analysis_job
            set status = 'FAILED', error = :error, finished_at = :now, locked_at = null
            where id = :id and status = 'RUNNING' and attempts = :claimed
            """)
        .param("id", jobId)
        .param("claimed", claimedAttempts)
        .param("error", error)
        .param("now", ts(now))
        .update() == 1;
  }

  /** 실패한 지 충분히 지난 작업을 다시 대기열로. attempts 는 그대로 두어 이전 회차의 늦은 워커가 끼어들지 못하게 한다. */
  public boolean requeueFailed(long jobId, Instant failedBefore, Instant now) {
    return jdbc.sql("""
            update analysis_job
            set status = 'QUEUED', attempt_base = attempts, error = null, finished_at = null, next_run_at = :now
            where id = :id and status = 'FAILED' and finished_at < :before
            """)
        .param("id", jobId)
        .param("before", ts(failedBefore))
        .param("now", ts(now))
        .update() == 1;
  }

  /** 근거, 분석기 결과, 작업 상태를 한 트랜잭션으로. 내 claim 이 아니면 아무것도 건드리지 않고 false. */
  @Transactional
  public boolean recordPermissionResult(long jobId, int claimedAttempts, List<Finding> findings, String payloadJson,
      String toolKind, Instant now) {
    // 첫 문장이 행 잠금을 잡아 같은 작업에 대한 동시 기록을 직렬화한다
    int applied = jdbc.sql("""
            update analysis_job
            set status = 'DONE', tool_kind = :kind, error = null, finished_at = :now, locked_at = null
            where id = :id and status = 'RUNNING' and attempts = :claimed
            """)
        .param("id", jobId)
        .param("claimed", claimedAttempts)
        .param("kind", toolKind)
        .param("now", ts(now))
        .update();
    if (applied != 1) {
      return false;
    }
    jdbc.sql("delete from finding where job_id = :id").param("id", jobId).update();
    template.batchUpdate(
        "insert into finding (job_id, category, rule_id, file_path, line, snippet) values (?, ?, ?, ?, ?, ?)",
        findings, 200, (ps, f) -> {
          ps.setLong(1, jobId);
          ps.setString(2, f.category());
          ps.setString(3, f.ruleId());
          ps.setString(4, f.filePath());
          ps.setInt(5, f.line());
          ps.setString(6, f.snippet());
        });
    jdbc.sql("""
            insert into analyzer_result (job_id, analyzer, status, payload, finished_at)
            values (:id, 'PERMISSION', 'DONE', cast(:payload as jsonb), :now)
            on conflict (job_id, analyzer) do update
            set status = excluded.status, payload = excluded.payload, error = null, finished_at = excluded.finished_at
            """)
        .param("id", jobId)
        .param("payload", payloadJson)
        .param("now", ts(now))
        .update();
    return true;
  }

  /** 워커가 작업 도중 죽으면 RUNNING 으로 남는다. 오래된 것을 다시 대기열로, 시도 상한이면 FAILED. */
  public int recoverStale(Instant lockedBefore, int maxAttempts, Instant now) {
    return jdbc.sql("""
            update analysis_job
            set status = case when attempts - attempt_base >= :max then 'FAILED' else 'QUEUED' end,
                error = case when attempts - attempt_base >= :max then '분석이 여러 번 중단되었습니다' else error end,
                finished_at = case when attempts - attempt_base >= :max then :now else finished_at end,
                locked_at = null
            where status = 'RUNNING' and locked_at < :before
            """)
        .param("max", maxAttempts)
        .param("now", ts(now))
        .param("before", ts(lockedBefore))
        .update();
  }

  public Optional<JobView> findJob(long jobId) {
    return jdbc.sql("""
            select j.id, r.owner, r.name, j.commit_sha, j.status, j.tool_kind, j.error, ar.payload::text as permission
            from analysis_job j
            join repository r on r.id = j.repo_id
            left join analyzer_result ar on ar.job_id = j.id and ar.analyzer = 'PERMISSION'
            where j.id = :id
            """)
        .param("id", jobId)
        .query((rs, n) -> new JobView(rs.getLong("id"), rs.getString("owner"), rs.getString("name"),
            rs.getString("commit_sha"), rs.getString("status"), rs.getString("tool_kind"), rs.getString("error"),
            rs.getString("permission")))
        .optional();
  }

  public List<Finding> findFindings(long jobId) {
    return jdbc.sql("""
            select category, rule_id, file_path, line, snippet
            from finding where job_id = :id
            order by id
            """)
        .param("id", jobId)
        .query((rs, n) -> new Finding(rs.getString("category"), rs.getString("rule_id"), rs.getString("file_path"),
            rs.getInt("line"), rs.getString("snippet")))
        .list();
  }

  // PostgreSQL 드라이버는 Instant 를 직접 받지 못한다
  private static OffsetDateTime ts(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
