package com.jeffkm.repoinspect;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeffkm.repoinspect.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "repo-inspect.worker.enabled=false")
@Import(PostgresTestConfig.class)
class RepoInspectApplicationTests {

  @Autowired
  JdbcClient jdbc;

  @Test
  void 스키마가_만들어진다() {
    Integer tables = jdbc.sql("""
            select count(*) from information_schema.tables
            where table_name in ('repository', 'analysis_job', 'analyzer_result', 'finding')
            """)
        .query(Integer.class)
        .single();
    assertThat(tables).isEqualTo(4);
  }
}
