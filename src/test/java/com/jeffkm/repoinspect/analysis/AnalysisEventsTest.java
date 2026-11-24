package com.jeffkm.repoinspect.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeffkm.repoinspect.github.RepoRef;
import com.jeffkm.repoinspect.job.JobRepository;
import com.jeffkm.repoinspect.support.PostgresTestConfig;
import java.time.Instant;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// 스케줄 푸시는 사실상 끄고(1시간) 테스트가 push() 를 직접 부른다
@SpringBootTest(properties = {"repo-inspect.worker.enabled=false",
    "repo-inspect.events.poll-ms=3600000",
    "repo-inspect.events.max-subscribers=2"})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AnalysisEventsTest {

  @Autowired
  MockMvc mvc;
  @MockitoSpyBean
  JobRepository jobs;
  @Autowired
  JobEvents events;
  @Autowired
  JdbcClient jdbc;

  @BeforeEach
  void clean() {
    jdbc.sql("truncate finding, analyzer_result, analysis_job, repository restart identity cascade").update();
  }

  // 테스트가 중간에 실패해도 열린 구독이 다음 테스트의 상한을 차지하지 않게 닫는다
  @AfterEach
  void closeAll() {
    jdbc.sql("update analysis_job set status = 'DONE'").update();
    events.push();
  }

  long job(char shaChar) {
    long repoId = jobs.upsertRepository(new RepoRef("acme", "tool"));
    return jobs.enqueue(repoId, String.valueOf(shaChar).repeat(40), Instant.now());
  }

  void setStatus(long id, String status) {
    jdbc.sql("update analysis_job set status = :s where id = :id").param("s", status).param("id", id).update();
  }

  MvcResult open(long id) throws Exception {
    return mvc.perform(get("/api/analyses/{id}/events", id).accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(request().asyncStarted())
        .andReturn();
  }

  static int count(String text, String part) {
    return text.split(Pattern.quote(part), -1).length - 1;
  }

  @Test
  void 끝난_작업은_상태_하나를_보내고_바로_닫는다() throws Exception {
    long id = job('a');
    setStatus(id, "DONE");

    MvcResult r = open(id);

    assertThat(r.getResponse().getContentType()).startsWith("text/event-stream");
    assertThat(r.getResponse().getContentAsString()).contains("event:status").contains("\"status\":\"DONE\"");
    mvc.perform(asyncDispatch(r)).andExpect(status().isOk());
    assertThat(events.subscriberCount()).isZero();
  }

  @Test
  void 상태가_바뀔_때만_보내고_끝나면_닫는다() throws Exception {
    long id = job('b');
    MvcResult r = open(id);
    assertThat(r.getResponse().getContentAsString()).contains("\"status\":\"QUEUED\"");

    events.push();
    assertThat(count(r.getResponse().getContentAsString(), "event:status")).isEqualTo(1);

    setStatus(id, "RUNNING");
    events.push();
    assertThat(r.getResponse().getContentAsString()).contains("\"status\":\"RUNNING\"");

    setStatus(id, "PARTIAL");
    events.push();
    assertThat(r.getResponse().getContentAsString()).contains("\"status\":\"PARTIAL\"");
    assertThat(count(r.getResponse().getContentAsString(), "event:status")).isEqualTo(3);
    mvc.perform(asyncDispatch(r)).andExpect(status().isOk());
    assertThat(events.subscriberCount()).isZero();
  }

  @Test
  void 없는_작업은_JSON_404() throws Exception {
    mvc.perform(get("/api/analyses/999/events").accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").value("분석을 찾을 수 없습니다"));
  }

  @Test
  void 구독자가_상한이면_JSON_503() throws Exception {
    open(job('c'));
    open(job('d'));

    mvc.perform(get("/api/analyses/{id}/events", job('e')).accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false));
    assertThat(events.subscriberCount()).isEqualTo(2);
  }

  @Test
  void 상태_조회_중에_새로_구독한_작업은_닫지_않는다() throws Exception {
    long first = job('f');
    open(first);
    long late = job('g');
    // 조회가 끝나기 전에 새 작업이 구독하는 상황을 만든다
    doAnswer(inv -> {
      Object statuses = inv.callRealMethod();
      events.subscribe(late, "QUEUED");
      return statuses;
    }).when(jobs).findStatuses(anyCollection());

    events.push();

    assertThat(events.subscriberCount()).isEqualTo(2);
  }
}
