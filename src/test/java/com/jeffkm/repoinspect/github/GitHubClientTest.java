package com.jeffkm.repoinspect.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jeffkm.repoinspect.archive.LimitExceededException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitHubClientTest {

  static final String SHA = "a".repeat(40);
  static final RepoRef REPO = new RepoRef("acme", "tool");
  static final Instant NOW = Instant.parse("2026-11-22T00:00:00Z");

  WireMockServer server;

  @BeforeEach
  void start() {
    server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  GitHubClient client(String token, long maxTarballBytes) {
    return new GitHubClient(new GitHubProperties(server.baseUrl(), token, maxTarballBytes),
        Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
  }

  @Test
  void 기본_브랜치의_최신_커밋_SHA_를_얻는다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(200)
        .withBody("{\"default_branch\": \"main\"}")));
    server.stubFor(get("/repos/acme/tool/commits/main")
        .withHeader("Accept", equalTo("application/vnd.github.sha"))
        .willReturn(aResponse().withStatus(200).withBody(SHA)));

    assertThat(client("secret", 1000).resolveHeadSha(REPO)).isEqualTo(SHA);
    server.verify(getRequestedFor(urlEqualTo("/repos/acme/tool")).withHeader("Authorization", equalTo("Bearer secret")));
  }

  @Test
  void 토큰이_없으면_인증_헤더를_보내지_않는다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(200)
        .withBody("{\"default_branch\": \"main\"}")));
    server.stubFor(get("/repos/acme/tool/commits/main").willReturn(aResponse().withStatus(200).withBody(SHA)));

    client("", 1000).resolveHeadSha(REPO);

    assertThat(server.findAll(getRequestedFor(urlEqualTo("/repos/acme/tool"))).get(0).containsHeader("Authorization"))
        .isFalse();
  }

  @Test
  void 없는_저장소는_RepoNotFoundException() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO)).isInstanceOf(RepoNotFoundException.class);
  }

  @Test
  void 한도_초과_403_은_재설정_시각을_담아_RateLimitedException() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(403)
        .withHeader("x-ratelimit-remaining", "0")
        .withHeader("x-ratelimit-reset", String.valueOf(NOW.getEpochSecond() + 900))));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class,
            e -> assertThat(e.resetAt()).isEqualTo(NOW.plusSeconds(900)));
  }

  @Test
  void 보조_한도_429_는_Retry_After_를_따른다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(429).withHeader("retry-after", "30")));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class,
            e -> assertThat(e.resetAt()).isEqualTo(NOW.plusSeconds(30)));
  }

  @Test
  void 한도가_남은_403_은_일반_오류() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(403)
        .withHeader("x-ratelimit-remaining", "10")));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(GitHubException.class, e -> assertThat(e.status()).isEqualTo(403));
  }

  @Test
  void SHA_형식이_아니면_오류() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(200)
        .withBody("{\"default_branch\": \"main\"}")));
    server.stubFor(get("/repos/acme/tool/commits/main").willReturn(aResponse().withStatus(200).withBody("<html>")));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO)).isInstanceOf(GitHubException.class);
  }

  @Test
  void 압축_파일은_리다이렉트를_따라가서_받는다() throws Exception {
    server.stubFor(get("/repos/acme/tool/tarball/" + SHA).willReturn(aResponse().withStatus(302)
        .withHeader("Location", server.baseUrl() + "/codeload/acme/tool/" + SHA)));
    server.stubFor(get("/codeload/acme/tool/" + SHA).willReturn(aResponse().withStatus(200).withBody("tarbytes")));

    try (InputStream in = client("", 1000).openTarball(REPO, SHA)) {
      assertThat(new String(in.readAllBytes())).isEqualTo("tarbytes");
    }
  }

  @Test
  void 압축_파일이_상한을_넘으면_읽다가_멈춘다() {
    server.stubFor(get("/repos/acme/tool/tarball/" + SHA).willReturn(aResponse().withStatus(200)
        .withBody("x".repeat(100))));

    assertThatThrownBy(() -> {
      try (InputStream in = client("", 10).openTarball(REPO, SHA)) {
        in.readAllBytes();
      }
    }).isInstanceOf(LimitExceededException.class);
  }

  @Test
  void 다른_호스트로_리다이렉트되면_토큰을_빼고_따라간다() throws Exception {
    String other = "http://127.0.0.1:" + server.port();
    server.stubFor(get("/repos/acme/tool/tarball/" + SHA).willReturn(aResponse().withStatus(302)
        .withHeader("Location", other + "/codeload/acme/tool/" + SHA)));
    server.stubFor(get("/codeload/acme/tool/" + SHA).willReturn(aResponse().withStatus(200).withBody("tarbytes")));

    try (InputStream in = client("secret", 1000).openTarball(REPO, SHA)) {
      assertThat(new String(in.readAllBytes())).isEqualTo("tarbytes");
    }

    assertThat(server.findAll(getRequestedFor(urlEqualTo("/codeload/acme/tool/" + SHA))).get(0)
        .containsHeader("Authorization")).isFalse();
    assertThat(server.findAll(getRequestedFor(urlEqualTo("/repos/acme/tool/tarball/" + SHA))).get(0)
        .containsHeader("Authorization")).isTrue();
  }

  @Test
  void 리다이렉트는_한_번만_따라간다() {
    server.stubFor(get("/repos/acme/tool/tarball/" + SHA).willReturn(aResponse().withStatus(302)
        .withHeader("Location", server.baseUrl() + "/a")));
    server.stubFor(get("/a").willReturn(aResponse().withStatus(302).withHeader("Location", server.baseUrl() + "/b")));

    assertThatThrownBy(() -> client("", 1000).openTarball(REPO, SHA)).isInstanceOf(GitHubException.class);
  }

  @Test
  void 아주_큰_Retry_After_와_reset_은_1시간으로_자른다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(429)
        .withHeader("retry-after", "99999999999999")));
    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class,
            e -> assertThat(e.resetAt()).isEqualTo(NOW.plusSeconds(3600)));

    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(403)
        .withHeader("x-ratelimit-remaining", "0").withHeader("x-ratelimit-reset", "99999999999999")));
    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class,
            e -> assertThat(e.resetAt()).isEqualTo(NOW.plusSeconds(3600)));
  }

  @Test
  void 숫자가_아닌_Retry_After_는_기본_60초로_처리한다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(429)
        .withHeader("retry-after", "Wed, 21 Oct 2015 07:28:00 GMT")));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class,
            e -> assertThat(e.resetAt()).isEqualTo(NOW.plusSeconds(60)));
  }

  @Test
  void 과거의_재설정_시각은_지금으로_올린다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(403)
        .withHeader("x-ratelimit-remaining", "0")
        .withHeader("x-ratelimit-reset", String.valueOf(NOW.getEpochSecond() - 500))));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class,
            e -> assertThat(e.resetAt()).isEqualTo(NOW));
  }

  @Test
  void Retry_After_가_있는_403_은_보조_한도다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(403)
        .withHeader("x-ratelimit-remaining", "10").withHeader("retry-after", "30")));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class,
            e -> assertThat(e.resetAt()).isEqualTo(NOW.plusSeconds(30)));
  }

  @Test
  void JSON_이_깨지면_원인_예외를_보존한다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(200).withBody("{not json")));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(GitHubException.class, e -> assertThat(e.getCause()).isNotNull());
  }

  @Test
  void 빈_저장소는_EmptyRepositoryException() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(200)
        .withBody("{\"default_branch\": \"main\"}")));
    server.stubFor(get("/repos/acme/tool/commits/main").willReturn(aResponse().withStatus(409)
        .withBody("{\"message\": \"Git Repository is empty.\"}")));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO)).isInstanceOf(EmptyRepositoryException.class);
  }

  @Test
  void openTarball_은_SHA_형식이_아니면_요청하지_않는다() {
    assertThatThrownBy(() -> client("", 1000).openTarball(REPO, "../../x")).isInstanceOf(IllegalArgumentException.class);
    assertThat(server.findAll(anyRequestedFor(anyUrl()))).isEmpty();
  }

  @Test
  void file_주소로의_리다이렉트는_거절한다() {
    server.stubFor(get("/repos/acme/tool/tarball/" + SHA).willReturn(aResponse().withStatus(302)
        .withHeader("Location", "file:///etc/passwd")));

    assertThatThrownBy(() -> client("", 1000).openTarball(REPO, SHA)).isInstanceOf(GitHubException.class);
  }

  @Test
  void 리다이렉트는_http_https_만_허용하고_https_에서_http_로_내려가지_않는다() {
    assertThat(GitHubClient.allowedRedirect(URI.create("https://api.github.com/a"),
        URI.create("https://codeload.github.com/b"))).isTrue();
    assertThat(GitHubClient.allowedRedirect(URI.create("http://127.0.0.1:1/a"), URI.create("http://127.0.0.1:1/b")))
        .isTrue();
    assertThat(GitHubClient.allowedRedirect(URI.create("https://api.github.com/a"),
        URI.create("http://codeload.github.com/b"))).isFalse();
    assertThat(GitHubClient.allowedRedirect(URI.create("https://api.github.com/a"), URI.create("file:///etc/passwd")))
        .isFalse();
  }

  @Test
  void 음수_대기_값은_지금으로_올린다() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(429)
        .withHeader("retry-after", "-9223372036854775808")));
    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.resetAt()).isEqualTo(NOW));

    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(403)
        .withHeader("x-ratelimit-remaining", "0").withHeader("x-ratelimit-reset", "-9223372036854775808")));
    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.resetAt()).isEqualTo(NOW));
  }

  @Test
  void 같은_호스트_리다이렉트는_토큰을_유지한다() throws Exception {
    server.stubFor(get("/repos/acme/tool/tarball/" + SHA).willReturn(aResponse().withStatus(302)
        .withHeader("Location", server.baseUrl() + "/codeload/acme/tool/" + SHA)));
    server.stubFor(get("/codeload/acme/tool/" + SHA).willReturn(aResponse().withStatus(200).withBody("t")));

    try (InputStream in = client("secret", 1000).openTarball(REPO, SHA)) {
      in.readAllBytes();
    }

    assertThat(server.findAll(getRequestedFor(urlEqualTo("/codeload/acme/tool/" + SHA))).get(0)
        .getHeader("Authorization")).isEqualTo("Bearer secret");
  }

  @Test
  void 서버_오류는_상태_코드를_담은_GitHubException() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOfSatisfying(GitHubException.class, e -> assertThat(e.status()).isEqualTo(503));
  }

  @Test
  void 연결이_끊기면_원인을_담은_GitHubException() {
    server.stubFor(get("/repos/acme/tool").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(() -> client("", 1000).resolveHeadSha(REPO))
        .isInstanceOf(GitHubException.class).hasCauseInstanceOf(IOException.class);
  }
}
