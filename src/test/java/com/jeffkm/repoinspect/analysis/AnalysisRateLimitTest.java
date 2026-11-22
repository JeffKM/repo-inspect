package com.jeffkm.repoinspect.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeffkm.repoinspect.github.GitHubSource;
import com.jeffkm.repoinspect.support.PostgresTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"repo-inspect.worker.enabled=false", "repo-inspect.rate-limit.analyses-per-minute=2"})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AnalysisRateLimitTest {

  @Autowired
  MockMvc mvc;
  @Autowired
  StringRedisTemplate redis;
  @Autowired
  JdbcClient jdbc;
  @MockitoBean
  GitHubSource github;

  @BeforeEach
  void clean() throws InterruptedException {
    jdbc.sql("truncate finding, analyzer_result, analysis_job, repository restart identity cascade").update();
    redis.execute((RedisCallback<Void>) c -> {
      c.serverCommands().flushAll();
      return null;
    });
    when(github.resolveHeadSha(any())).thenReturn("f".repeat(40));
    // 1분 창 경계에서 요청이 두 창으로 갈리지 않게, 분이 끝나기 5초 전이면 다음 분까지 기다린다
    long second = Instant.now().getEpochSecond() % 60;
    if (second >= 55) {
      Thread.sleep((60 - second) * 1000 + 100);
    }
  }

  ResultActions analyze(String ip) throws Exception {
    return mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
        .content("{\"url\": \"https://github.com/acme/tool\"}")
        .with(r -> {
          r.setRemoteAddr(ip);
          return r;
        }));
  }

  @Test
  void 같은_IP_가_1분_상한을_넘으면_429_와_Retry_After() throws Exception {
    analyze("203.0.113.7").andExpect(status().isAccepted());
    analyze("203.0.113.7").andExpect(status().isAccepted());

    analyze("203.0.113.7")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").value("분석 요청이 많습니다. 잠시 뒤 다시 시도해 주세요"));
  }

  @Test
  void 다른_IP_는_따로_센다() throws Exception {
    analyze("203.0.113.7").andExpect(status().isAccepted());
    analyze("203.0.113.7").andExpect(status().isAccepted());

    analyze("203.0.113.8").andExpect(status().isAccepted());
  }

  @Test
  void 결과_조회는_제한하지_않는다() throws Exception {
    analyze("203.0.113.7");
    analyze("203.0.113.7");
    analyze("203.0.113.7").andExpect(status().isTooManyRequests());

    for (int i = 0; i < 5; i++) {
      mvc.perform(get("/api/analyses/1").with(r -> {
        r.setRemoteAddr("203.0.113.7");
        return r;
      })).andExpect(status().isOk());
    }
  }
}
