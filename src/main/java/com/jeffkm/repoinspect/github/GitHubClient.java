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
  private static final long MAX_WAIT_SECONDS = 3600;
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
        .followRedirects(HttpClient.Redirect.NEVER) // 토큰이 다른 호스트로 새지 않게 직접 따라간다
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
    // 본문 읽기 시간 상한은 HttpClient 에 없어서 AnalysisWorker 가 기한 뒤 스트림을 닫는 것으로 막는다
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
    HttpResponse<T> res = exchange(req, handler);
    if (isRedirect(res.statusCode())) {
      // 압축 파일은 codeload 로 302 리다이렉트된다. 한 번만 따라간다
      Optional<String> location = res.headers().firstValue("location");
      closeQuietly(res.body());
      if (location.isEmpty()) {
        throw new GitHubException(res.statusCode(), "리다이렉트 위치가 없습니다");
      }
      URI target = req.uri().resolve(location.get().strip());
      res = exchange(redirected(req, target), handler);
      if (isRedirect(res.statusCode())) {
        closeQuietly(res.body());
        throw new GitHubException(res.statusCode(), "리다이렉트가 두 번 이상입니다");
      }
    }
    int status = res.statusCode();
    if (status >= 200 && status < 300) {
      return res;
    }
    closeQuietly(res.body());
    if (status == 404) {
      throw new RepoNotFoundException(res.uri().getPath());
    }
    if (status == 409) {
      throw new EmptyRepositoryException(res.uri().getPath());
    }
    boolean exhausted = "0".equals(res.headers().firstValue("x-ratelimit-remaining").orElse(null));
    boolean retryAfter = res.headers().firstValue("retry-after").isPresent();
    if (status == 429 || (status == 403 && (exhausted || retryAfter))) {
      throw new RateLimitedException(resetAt(res));
    }
    throw new GitHubException(status, "GitHub 응답 " + status);
  }

  private <T> HttpResponse<T> exchange(HttpRequest req, BodyHandler<T> handler) {
    try {
      return http.send(req, handler);
    } catch (IOException e) {
      throw new GitHubException(0, "GitHub 호출 실패: " + e.getClass().getSimpleName(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GitHubException(0, "GitHub 호출이 중단되었습니다", e);
    }
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }

  /** 같은 호스트(포트 포함)일 때만 인증 헤더를 유지한다. */
  private static HttpRequest redirected(HttpRequest req, URI target) {
    boolean sameHost = target.getHost() != null && target.getHost().equalsIgnoreCase(req.uri().getHost())
        && effectivePort(target) == effectivePort(req.uri());
    HttpRequest.Builder b = HttpRequest.newBuilder(target).GET();
    req.timeout().ifPresent(b::timeout);
    req.headers().map().forEach((name, values) -> {
      if (sameHost || !name.equalsIgnoreCase("Authorization")) {
        values.forEach(v -> b.header(name, v));
      }
    });
    return b.build();
  }

  private static int effectivePort(URI u) {
    return u.getPort() != -1 ? u.getPort() : "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
  }

  private Instant resetAt(HttpResponse<?> res) {
    Instant now = clock.instant();
    Instant fallback = now.plus(DEFAULT_RETRY);
    Instant at;
    try {
      Optional<String> retryAfter = res.headers().firstValue("retry-after");
      if (retryAfter.isPresent()) {
        at = now.plusSeconds(Math.min(Long.parseLong(retryAfter.get().strip()), MAX_WAIT_SECONDS));
      } else {
        at = res.headers().firstValue("x-ratelimit-reset")
            .map(v -> Instant.ofEpochSecond(Math.min(Long.parseLong(v.strip()), now.getEpochSecond() + MAX_WAIT_SECONDS)))
            .orElse(fallback);
      }
    } catch (NumberFormatException e) {
      at = fallback; // HTTP-date 등 숫자가 아닌 값
    }
    return at.isBefore(now) ? now : at;
  }

  private com.fasterxml.jackson.databind.JsonNode readJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      throw new GitHubException(0, "GitHub 응답을 읽지 못했습니다", e);
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
