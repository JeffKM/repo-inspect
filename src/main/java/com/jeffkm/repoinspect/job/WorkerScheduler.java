package com.jeffkm.repoinspect.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 워커를 주기적으로 돌린다. 테스트는 repo-inspect.worker.enabled=false 로 끄고 runOnce 를 직접 부른다. */
@Component
@ConditionalOnProperty(name = "repo-inspect.worker.enabled", havingValue = "true", matchIfMissing = true)
class WorkerScheduler {

  private static final int MAX_JOBS_PER_POLL = 10;
  private static final Duration STALE_AFTER = Duration.ofMinutes(10);
  private static final Logger log = LoggerFactory.getLogger(WorkerScheduler.class);

  private final AnalysisWorker worker;
  private final JobRepository jobs;
  private final Clock clock;

  WorkerScheduler(AnalysisWorker worker, JobRepository jobs, Clock clock) {
    this.worker = worker;
    this.jobs = jobs;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${repo-inspect.worker.poll-ms:1000}")
  void poll() {
    int done = 0;
    while (done < MAX_JOBS_PER_POLL && worker.runOnce()) {
      done++;
    }
  }

  @Scheduled(fixedDelay = 60_000)
  void recoverStale() {
    Instant now = clock.instant();
    int recovered = jobs.recoverStale(now.minus(STALE_AFTER), AnalysisWorker.MAX_ATTEMPTS, now);
    if (recovered > 0) {
      log.warn("멈춘 작업 {}개를 되돌렸습니다", recovered);
    }
  }
}
