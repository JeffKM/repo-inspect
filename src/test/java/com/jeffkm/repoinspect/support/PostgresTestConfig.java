package com.jeffkm.repoinspect.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 테스트 공용 PostgreSQL 과 Redis. 빈으로 두면 같은 설정의 테스트끼리 컨테이너를 재사용한다. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:16-alpine");
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
  }
}
