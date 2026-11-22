package com.jeffkm.repoinspect.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시각은 언제나 Clock 빈에서 얻는다. 테스트에서 시각을 고정하거나 비교할 수 있게. */
@Configuration
public class TimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
