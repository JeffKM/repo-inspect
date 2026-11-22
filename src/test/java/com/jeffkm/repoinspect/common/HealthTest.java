package com.jeffkm.repoinspect.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeffkm.repoinspect.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "repo-inspect.worker.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class HealthTest {

  @Autowired
  MockMvc mvc;

  @Test
  void 상태_확인은_UP_만_알리고_세부_정보는_숨긴다() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
  }

  @Test
  void 상태_확인_외의_actuator_는_열지_않는다() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
  }
}
