package com.jeffkm.repoinspect.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeffkm.repoinspect.archive.LimitedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GitHubClient implements GitHubSource {

  private static final Duration META_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration TARBALL_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration DEFAULT_RETRY = Duration.ofSeconds(60);

  private final GitHubProperties props;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final HttpClient http;

  public GitHubClient(GitHubProperties props, Clock clock, ObjectMapper objectMapper) {
    this.props = props;
    this.clock = clock;
    this.objectMapper = objectMapper;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL) // 압축 파일은 codeload 로 302 리다이렉트된다
        .build();
  }

  @Override
  public String resolveHeadSha(RepoRef repo) {
    String base = "/repos/%s/%s".formatted(repo.owner(), repo.name());
    HttpResponse<String> meta = send(request(base, "application/vnd.github+json", META_TIMEOUT), BodyHandlers.ofString());
    String branch = readJson(meta.body()).path("default_branch").asText();
    if (branch.isEmpty()) {
      throw new GitHubException(meta.statusCode(), "기본 브랜치를 알 수 없습니다");
    }
    String commitPath = base + "/commits/" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
    HttpResponse<String> commit =
        send(request(commitPath, "application/vnd.github.sha", META_TIMEOUT), BodyHandlers.ofString());
    String sha = commit.body().strip();
    if (!sha.matches("[0-9a-f]{40}")) {
      throw new GitHubException(commit.statusCode(), "커밋 SHA 형식이 아닙니다");
    }
    return sha;
  }

  @Override
  public InputStream openTarball(RepoRef repo, String sha) {
    String path = "/repos/%s/%s/tarball/%s".formatted(repo.owner(), repo.name(), sha);
    HttpResponse<InputStream> res =
        send(request(path, "application/vnd.github+json", TARBALL_TIMEOUT), BodyHandlers.ofInputStream());
    // shortcut: 본문을 다 받는 시간에는 상한이 없다(크기 상한만). 느린 응답이 워커를 오래 잡는 일이 생기면 읽기 시간 상한을 추가
    return new LimitedInputStream(res.body(), props.maxTarballBytes());
  }

  private HttpRequest request(String path, String accept, Duration timeout) {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(props.baseUrl() + path))
        .timeout(timeout)
        .header("Accept", accept)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "repo-inspect");
    if (props.token() != null && !props.token().isBlank()) {
      b.header("Authorization", "Bearer " + props.token());
    }
    return b.GET().build();
  }

  private <T> HttpResponse<T> send(HttpRequest req, BodyHandler<T> handler) {
    HttpResponse<T> res;
    try {
      res = http.send(req, handler);
    } catch (IOException e) {
      throw new GitHubException(0, "GitHub 호출 실패: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GitHubException(0, "GitHub 호출이 중단되었습니다");
    }
    int status = res.statusCode();
    if (status >= 200 && status < 300) {
      return res;
    }
    closeQuietly(res.body());
    if (status == 404) {
      throw new RepoNotFoundException(req.uri().getPath());
    }
    boolean exhausted = "0".equals(res.headers().firstValue("x-ratelimit-remaining").orElse(null));
    if (status == 429 || (status == 403 && exhausted)) {
      throw new RateLimitedException(resetAt(res));
    }
    throw new GitHubException(status, "GitHub 응답 " + status);
  }

  private Instant resetAt(HttpResponse<?> res) {
    Optional<String> retryAfter = res.headers().firstValue("retry-after");
    if (retryAfter.isPresent()) {
      return clock.instant().plusSeconds(Long.parseLong(retryAfter.get().strip()));
    }
    return res.headers().firstValue("x-ratelimit-reset")
        .map(v -> Instant.ofEpochSecond(Long.parseLong(v.strip())))
        .orElse(clock.instant().plus(DEFAULT_RETRY));
  }

  private com.fasterxml.jackson.databind.JsonNode readJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      throw new GitHubException(0, "GitHub 응답을 읽지 못했습니다");
    }
  }

  private static void closeQuietly(Object body) {
    if (body instanceof InputStream in) {
      try {
        in.close();
      } catch (IOException ignored) {
        // 이미 실패 응답이라 닫기 실패는 무시한다
      }
    }
  }
}
