package com.jeffkm.repoinspect.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeffkm.repoinspect.github.GitHubSource;
import com.jeffkm.repoinspect.github.RepoRef;
import com.jeffkm.repoinspect.job.JobRepository;
import com.jeffkm.repoinspect.job.JobView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalysisService {

  private final GitHubSource github;
  private final JobRepository jobs;
  private final ObjectMapper objectMapper;

  public AnalysisService(GitHubSource github, JobRepository jobs, ObjectMapper objectMapper) {
    this.github = github;
    this.jobs = jobs;
    this.objectMapper = objectMapper;
  }

  /** 최신 커밋을 정해 작업을 만든다. 같은 커밋이면 기존 작업을 돌려준다. */
  public CreatedAnalysis create(String url) {
    RepoRef ref = RepoRef.parse(url);
    String sha = github.resolveHeadSha(ref);
    long jobId = jobs.enqueue(jobs.upsertRepository(ref), sha);
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
