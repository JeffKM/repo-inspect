package com.jeffkm.repoinspect.analysis;

/** 같은 IP 의 분석 요청이 1분 상한을 넘었다. */
public class TooManyRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyRequestsException(long retryAfterSeconds) {
    super("분석 요청 한도 초과");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
