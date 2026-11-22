package com.jeffkm.repoinspect.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeffkm.repoinspect.archive.ArchiveLimits;
import com.jeffkm.repoinspect.archive.ReadResult;
import com.jeffkm.repoinspect.archive.SafeTarReader;
import com.jeffkm.repoinspect.archive.Submodules;
import com.jeffkm.repoinspect.github.GitHubSource;
import com.jeffkm.repoinspect.github.RateLimitedException;
import com.jeffkm.repoinspect.github.RepoNotFoundException;
import com.jeffkm.repoinspect.permission.Finding;
import com.jeffkm.repoinspect.permission.PermissionScanner;
import com.jeffkm.repoinspect.permission.ScanResult;
import com.jeffkm.repoinspect.permission.ToolKind;
import com.jeffkm.repoinspect.permission.ToolKindDetector;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 대기열에서 작업 하나를 꺼내 압축 파일을 읽고 권한 명세서를 저장한다. */
@Component
public class AnalysisWorker {

  public static final int MAX_ATTEMPTS = 3;
  private static final long RETRY_BASE_SECONDS = 30;
  private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

  private final JobRepository jobs;
  private final GitHubSource github;
  private final ArchiveLimits limits;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final long readIdleSeconds;
  private final long readDeadlineSeconds;
  private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "read-deadline");
    thread.setDaemon(true);
    return thread;
  });

  public AnalysisWorker(JobRepository jobs, GitHubSource github, ArchiveLimits limits, Clock clock,
      ObjectMapper objectMapper,
      @Value("${repo-inspect.worker.read-idle-seconds:30}") long readIdleSeconds,
      @Value("${repo-inspect.worker.read-deadline-seconds:300}") long readDeadlineSeconds) {
    this.jobs = jobs;
    this.github = github;
    this.limits = limits;
    this.clock = clock;
    this.objectMapper = objectMapper;
    this.readIdleSeconds = readIdleSeconds;
    this.readDeadlineSeconds = readDeadlineSeconds;
  }

  @PreDestroy
  void shutdown() {
    deadlines.shutdownNow();
  }

  /** 꺼낼 작업이 있었으면 true. */
  public boolean runOnce() {
    Optional<ClaimedJob> claimed = jobs.claimNext(clock.instant());
    claimed.ifPresent(this::process);
    return claimed.isPresent();
  }

  private void process(ClaimedJob job) {
    int claimed = job.attempts();
    try (InputStream in = new WatchedInputStream(github.openTarball(job.repo(), job.sha()),
        Duration.ofSeconds(readIdleSeconds), Duration.ofSeconds(readDeadlineSeconds), deadlines)) {
      ReadResult read = SafeTarReader.read(in, limits);
      Set<ToolKind> kinds = ToolKindDetector.detect(read.files());
      ScanResult scan = PermissionScanner.scan(read.files());
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("truncated", read.truncated());
      payload.put("fileCount", read.files().size());
      payload.put("skippedFiles", read.skippedFiles());
      payload.put("domains", scan.domains());
      payload.put("domainsTruncated", scan.domainsTruncated());
      payload.put("findingsTruncated", scan.findingsTruncated());
      // 화면 첫 줄에 보여 줄 범주별 수는 주요 근거만 센다
      payload.put("primaryCounts", scan.findings().stream().filter(Finding::primary)
          .collect(Collectors.groupingBy(Finding::category, TreeMap::new, Collectors.counting())));
      payload.put("secondaryFindings", scan.findings().stream().filter(f -> !f.primary()).count());
      payload.put("submodules", Submodules.paths(read.files()));
      settle(job, jobs.recordPermissionResult(job.id(), claimed, scan.findings(),
          objectMapper.writeValueAsString(payload), ToolKindDetector.label(kinds), clock.instant()));
    } catch (RateLimitedException e) {
      settle(job, jobs.reschedule(job.id(), claimed, e.resetAt(), false));
    } catch (RepoNotFoundException e) {
      settle(job, jobs.fail(job.id(), claimed, "저장소를 찾을 수 없습니다", clock.instant()));
    } catch (Exception e) {
      log.warn("분석 실패 job={} attempt={}", job.id(), claimed, e);
      // 실패한 때를 기준으로 잡는다. 시작 시각을 쓰면 기한 뒤 실패한 작업이 곧바로 다시 돈다
      Instant failedAt = clock.instant();
      int round = job.roundAttempts();
      if (round >= MAX_ATTEMPTS) {
        settle(job, jobs.fail(job.id(), claimed, "분석에 " + MAX_ATTEMPTS + "번 실패했습니다", failedAt));
      } else {
        settle(job, jobs.reschedule(job.id(), claimed, failedAt.plusSeconds(RETRY_BASE_SECONDS * round), true));
      }
    }
  }

  /** 상태 변경이 거부되면 다른 워커가 이어받은 것이므로 결과를 버린다. */
  private void settle(ClaimedJob job, boolean applied) {
    if (!applied) {
      log.info("작업 {}는 다른 워커가 이어받아 결과를 버립니다", job.id());
    }
  }
}
