package com.jeffkm.repoinspect.github;

import java.time.Instant;

/** GitHub 한도 초과. 실패가 아니라 resetAt 까지 미뤄야 하는 상태다. */
public class RateLimitedException extends RuntimeException {

  private final Instant resetAt;

  public RateLimitedException(Instant resetAt) {
    super("GitHub 한도 초과, 재설정 시각 " + resetAt);
    this.resetAt = resetAt;
  }

  public Instant resetAt() {
    return resetAt;
  }
}
