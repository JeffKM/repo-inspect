package com.jeffkm.repoinspect.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeffkm.repoinspect.github.GitHubSource;
import com.jeffkm.repoinspect.github.RateLimitedException;
import com.jeffkm.repoinspect.github.RepoNotFoundException;
import com.jeffkm.repoinspect.job.AnalysisWorker;
import com.jeffkm.repoinspect.support.PostgresTestConfig;
import com.jeffkm.repoinspect.support.TarGz;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "repo-inspect.worker.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AnalysisApiTest {

  static final String SHA = "e".repeat(40);

  @Autowired
  MockMvc mvc;
  @Autowired
  AnalysisWorker worker;
  @Autowired
  JdbcClient jdbc;
  @MockitoBean
  GitHubSource github;

  @BeforeEach
  void clean() {
    jdbc.sql("truncate finding, analyzer_result, analysis_job, repository restart identity cascade").update();
  }

  String body(String url) {
    return "{\"url\": \"" + url + "\"}";
  }

  @Test
  void 분석을_요청하면_202_와_작업_번호() throws Exception {
    when(github.resolveHeadSha(any())).thenReturn(SHA);

    mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
            .content(body("https://github.com/acme/tool")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.owner").value("acme"))
        .andExpect(jsonPath("$.data.name").value("tool"))
        .andExpect(jsonPath("$.data.commitSha").value(SHA))
        .andExpect(jsonPath("$.data.status").value("QUEUED"));
  }

  @Test
  void 같은_커밋을_다시_요청하면_같은_작업_번호() throws Exception {
    when(github.resolveHeadSha(any())).thenReturn(SHA);
    String url = body("https://github.com/acme/tool");

    String first = mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(url))
        .andReturn().getResponse().getContentAsString();
    String second = mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(url))
        .andReturn().getResponse().getContentAsString();

    org.assertj.core.api.Assertions.assertThat(jobId(first)).isEqualTo(jobId(second));
  }

  @Test
  void 작업이_끝나면_결과와_근거를_돌려준다() throws Exception {
    when(github.resolveHeadSha(any())).thenReturn(SHA);
    when(github.openTarball(any(), anyString())).thenReturn(new TarGz()
        .file("acme-tool-e/index.js", "const k = process.env.TOKEN;\nfetch('https://api.example.com')")
        .stream());
    String created = mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
            .content(body("github.com/acme/tool")))
        .andReturn().getResponse().getContentAsString();
    worker.runOnce();

    mvc.perform(get("/api/analyses/" + jobId(created)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DONE"))
        .andExpect(jsonPath("$.data.toolKind").value("GENERIC"))
        .andExpect(jsonPath("$.data.findings[0].ruleId").value("env.node"))
        .andExpect(jsonPath("$.data.findings[0].line").value(1))
        .andExpect(jsonPath("$.data.findings[1].ruleId").value("net.http_call"))
        .andExpect(jsonPath("$.data.permission.domains[0]").value("api.example.com"))
        .andExpect(jsonPath("$.data.permission.truncated").value(false));
  }

  @Test
  void 잘못된_주소는_400() throws Exception {
    mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
            .content(body("https://gitlab.com/acme/tool")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").value("github.com 저장소 주소만 받습니다"));
  }

  @Test
  void 주소가_없으면_400() throws Exception {
    mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 없는_저장소는_404() throws Exception {
    when(github.resolveHeadSha(any())).thenThrow(new RepoNotFoundException("/repos/acme/none"));

    mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
            .content(body("https://github.com/acme/none")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("저장소를 찾을 수 없습니다(공개 저장소만 분석합니다)"));
  }

  @Test
  void GitHub_한도_초과면_503_과_Retry_After() throws Exception {
    when(github.resolveHeadSha(any())).thenThrow(new RateLimitedException(Instant.now().plusSeconds(120)));

    mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
            .content(body("https://github.com/acme/tool")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void 없는_분석은_404() throws Exception {
    mvc.perform(get("/api/analyses/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("분석을 찾을 수 없습니다"));
  }

  static long jobId(String json) {
    return Long.parseLong(json.replaceAll(".*\"jobId\":(\\d+).*", "$1"));
  }
}
