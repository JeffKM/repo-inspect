package com.jeffkm.repoinspect.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeffkm.repoinspect.github.GitHubSource;
import com.jeffkm.repoinspect.github.RepoRef;
import com.jeffkm.repoinspect.job.JobRepository;
import com.jeffkm.repoinspect.job.JobView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalysisService {

  // 실패는 GitHub 일시 장애나 비공개였던 저장소처럼 시간이 지나면 풀릴 수 있다. 너무 자주 다시 돌리면 GitHub 한도를 쓴다
  static final Duration FAILED_RETRY_AFTER = Duration.ofMinutes(30);

  private final GitHubSource github;
  private final JobRepository jobs;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public AnalysisService(GitHubSource github, JobRepository jobs, ObjectMapper objectMapper, Clock clock) {
    this.github = github;
    this.jobs = jobs;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 최신 커밋을 정해 작업을 만든다. 같은 커밋이면 기존 작업을 돌려주고, 실패한 지 30분이 지났으면 다시 대기열에 넣는다. */
  public CreatedAnalysis create(String url) {
    RepoRef ref = RepoRef.parse(url);
    String sha = github.resolveHeadSha(ref);
    long jobId = jobs.enqueue(jobs.upsertRepository(ref), sha);
    Instant now = clock.instant();
    jobs.requeueFailed(jobId, now.minus(FAILED_RETRY_AFTER), now);
    JobView v = jobs.findJob(jobId).orElseThrow();
    return new CreatedAnalysis(v.id(), v.owner(), v.name(), v.commitSha(), v.status());
  }

  public AnalysisView get(long jobId) {
    JobView v = jobs.findJob(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "분석을 찾을 수 없습니다"));
    return new AnalysisView(v.id(), v.owner(), v.name(), v.commitSha(), v.status(), v.toolKind(), v.error(),
        jobs.findFindings(jobId), parse(v.permissionJson()));
  }

  private JsonNode parse(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("저장된 결과를 읽지 못했습니다", e);
    }
  }
}
