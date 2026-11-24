package com.jeffkm.repoinspect.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeffkm.repoinspect.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"repo-inspect.worker.enabled=false",
    "repo-inspect.web-origins=http://localhost:3000, https://web.example.com"})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CorsTest {

  @Autowired
  MockMvc mvc;

  @Test
  void 허용한_출처의_사전_요청은_통과() throws Exception {
    mvc.perform(options("/api/analyses")
            .header("Origin", "https://web.example.com")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://web.example.com"));
  }

  @Test
  void 모르는_출처의_사전_요청은_거절() throws Exception {
    mvc.perform(options("/api/analyses")
            .header("Origin", "https://other.example.com")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }

  @Test
  void 응답의_Retry_After_를_브라우저가_읽을_수_있다() throws Exception {
    mvc.perform(get("/api/analyses/999").header("Origin", "http://localhost:3000"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
        .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
  }
}
