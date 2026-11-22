package com.jeffkm.repoinspect.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeffkm.repoinspect.github.RepoRef;
import com.jeffkm.repoinspect.permission.Finding;
import com.jeffkm.repoinspect.support.PostgresTestConfig;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "repo-inspect.worker.enabled=false")
@Import(PostgresTestConfig.class)
class JobRepositoryTest {

  static final String SHA = "b".repeat(40);
  // next_run_at 기본값이 DB 의 now() 라서 테스트 기준 시각은 실제 현재보다 뒤여야 한다. DB 는 마이크로초까지라 초 단위로 자른다
  static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(3600);

  @Autowired
  JobRepository jobs;
  @Autowired
  JdbcClient jdbc;

  long repoId;

  @BeforeEach
  void clean() {
    jdbc.sql("truncate finding, analyzer_result, analysis_job, repository restart identity cascade").update();
    repoId = jobs.upsertRepository(new RepoRef("acme", "tool"));
  }

  @Test
  void 같은_저장소를_다시_넣으면_같은_id() {
    assertThat(jobs.upsertRepository(new RepoRef("acme", "tool"))).isEqualTo(repoId);
  }

  @Test
  void 같은_커밋_동시_요청_10건은_작업_하나로_합쳐진다() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Long>> futures = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      futures.add(pool.submit(() -> {
        start.await();
        return jobs.enqueue(repoId, SHA);
      }));
    }
    start.countDown();
    Set<Long> ids = ConcurrentHashMap.newKeySet();
    for (Future<Long> f : futures) {
      ids.add(f.get());
    }
    pool.shutdown();

    assertThat(ids).hasSize(1);
    assertThat(jdbc.sql("select count(*) from analysis_job").query(Integer.class).single()).isEqualTo(1);
  }

  @Test
  void 꺼내면_RUNNING_이_되고_시도_횟수가_오른다() {
    long id = jobs.enqueue(repoId, SHA);

    ClaimedJob claimed = jobs.claimNext(NOW.plusSeconds(1)).orElseThrow();

    assertThat(claimed).isEqualTo(new ClaimedJob(id, new RepoRef("acme", "tool"), SHA, 1));
    assertThat(jobs.findJob(id).orElseThrow().status()).isEqualTo("RUNNING");
    assertThat(jobs.claimNext(NOW.plusSeconds(1))).isEmpty();
  }

  @Test
  void 실행_시각이_아직이면_꺼내지_않는다() {
    long id = jobs.enqueue(repoId, SHA);
    jobs.reschedule(id, NOW.plusSeconds(600), true);

    assertThat(jobs.claimNext(NOW)).isEmpty();
    assertThat(jobs.claimNext(NOW.plusSeconds(601))).isPresent();
  }

  @Test
  void 두_워커가_동시에_꺼내도_한_작업은_한_워커만_받는다() throws Exception {
    jobs.enqueue(repoId, SHA);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Optional<ClaimedJob>>> futures = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      futures.add(pool.submit(() -> {
        start.await();
        return jobs.claimNext(Instant.now().plusSeconds(60));
      }));
    }
    start.countDown();
    int got = 0;
    for (Future<Optional<ClaimedJob>> f : futures) {
      if (f.get().isPresent()) {
        got++;
      }
    }
    pool.shutdown();

    assertThat(got).isEqualTo(1);
  }

  @Test
  void 한도_초과로_미룰_때는_시도_횟수를_되돌린다() {
    long id = jobs.enqueue(repoId, SHA);
    jobs.claimNext(NOW.plusSeconds(1));

    jobs.reschedule(id, NOW.plusSeconds(900), false);

    assertThat(attempts(id)).isZero();
    assertThat(nextRunAt(id)).isEqualTo(NOW.plusSeconds(900));
    assertThat(jobs.findJob(id).orElseThrow().status()).isEqualTo("QUEUED");
  }

  @Test
  void 결과_저장은_근거를_덮어쓰고_작업을_DONE_으로() {
    long id = jobs.enqueue(repoId, SHA);
    jobs.claimNext(NOW.plusSeconds(1));
    var first = List.of(new Finding("SECRETS", "env.node", "a.js", 1, "process.env.A"));
    var second = List.of(
        new Finding("SECRETS", "env.node", "a.js", 1, "process.env.A"),
        new Finding("SHELL_EXEC", "node.child_process", "b.js", 3, "require('child_process')"));

    jobs.recordPermissionResult(id, first, "{\"truncated\": false}", "SKILL", NOW);
    jobs.recordPermissionResult(id, second, "{\"truncated\": true}", "SKILL", NOW);

    JobView view = jobs.findJob(id).orElseThrow();
    assertThat(view.status()).isEqualTo("DONE");
    assertThat(view.toolKind()).isEqualTo("SKILL");
    assertThat(view.permissionJson()).contains("\"truncated\": true");
    assertThat(jobs.findFindings(id)).containsExactlyElementsOf(second);
  }

  @Test
  void 오래_멈춘_작업은_다시_대기열로_시도_상한이면_FAILED() {
    long retry = jobs.enqueue(repoId, SHA);
    long exhausted = jobs.enqueue(repoId, "c".repeat(40));
    jobs.claimNext(NOW);
    jobs.claimNext(NOW);
    jdbc.sql("update analysis_job set attempts = 3 where id = :id").param("id", exhausted).update();

    int recovered = jobs.recoverStale(NOW.plusSeconds(1), 3, NOW.plusSeconds(700));

    assertThat(recovered).isEqualTo(2);
    assertThat(jobs.findJob(retry).orElseThrow().status()).isEqualTo("QUEUED");
    assertThat(jobs.findJob(exhausted).orElseThrow().status()).isEqualTo("FAILED");
  }

  @Test
  void 실패로_끝내면_오류를_남긴다() {
    long id = jobs.enqueue(repoId, SHA);

    jobs.fail(id, "저장소를 찾을 수 없습니다", NOW);

    JobView view = jobs.findJob(id).orElseThrow();
    assertThat(view.status()).isEqualTo("FAILED");
    assertThat(view.error()).isEqualTo("저장소를 찾을 수 없습니다");
  }

  int attempts(long id) {
    return jdbc.sql("select attempts from analysis_job where id = :id").param("id", id).query(Integer.class).single();
  }

  Instant nextRunAt(long id) {
    return jdbc.sql("select next_run_at from analysis_job where id = :id").param("id", id)
        .query(OffsetDateTime.class).single().toInstant();
  }
}
