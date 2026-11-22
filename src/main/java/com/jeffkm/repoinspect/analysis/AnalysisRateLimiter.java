package com.jeffkm.repoinspect.analysis;

import java.time.Clock;
import java.time.Duration;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * IP별 분석 요청 수를 1분 고정 창으로 센다. 서버가 여러 대가 되어도 같은 수를 보도록 Redis 에 둔다.
 * Redis 에 닿지 못하면 막지 않는다. 제한이 잠깐 풀리는 쪽이 서비스 전체가 멈추는 쪽보다 낫다.
 */
@Component
public class AnalysisRateLimiter {

  private static final Duration KEY_TTL = Duration.ofSeconds(65);
  private static final Logger log = LoggerFactory.getLogger(AnalysisRateLimiter.class);

  private final StringRedisTemplate redis;
  private final Clock clock;
  private final int perMinute;

  public AnalysisRateLimiter(StringRedisTemplate redis, Clock clock,
      @Value("${repo-inspect.rate-limit.analyses-per-minute:10}") int perMinute) {
    this.redis = redis;
    this.clock = clock;
    this.perMinute = perMinute;
  }

  /** 허용이면 빈 값, 막히면 다음 창까지 남은 초(1~60). */
  public OptionalLong check(String clientIp) {
    long epochSecond = clock.instant().getEpochSecond();
    String key = "rl:analyses:" + clientIp + ":" + (epochSecond / 60);
    try {
      // 만료 시간은 키를 만들 때 같이 건다. INCR 뒤 EXPIRE 를 따로 하면 그 사이 장애로 만료 없는 키가 남아 계속 막힐 수 있다
      redis.opsForValue().setIfAbsent(key, "0", KEY_TTL);
      Long count = redis.opsForValue().increment(key);
      if (count != null && count > perMinute) {
        return OptionalLong.of(60 - epochSecond % 60);
      }
      return OptionalLong.empty();
    } catch (DataAccessException e) {
      log.warn("요청 제한 저장소에 닿지 못해 제한 없이 통과시킵니다: {}", e.getClass().getSimpleName());
      return OptionalLong.empty();
    }
  }
}
