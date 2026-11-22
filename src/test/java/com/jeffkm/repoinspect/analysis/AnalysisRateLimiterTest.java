package com.jeffkm.repoinspect.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class AnalysisRateLimiterTest {

  @Test
  void 저장소에_닿지_못하면_막지_않는다() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

    AnalysisRateLimiter limiter = new AnalysisRateLimiter(redis, Clock.systemUTC(), 1);

    assertThat(limiter.check("203.0.113.7")).isEmpty();
  }
}
