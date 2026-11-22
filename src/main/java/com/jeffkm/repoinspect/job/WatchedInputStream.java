package com.jeffkm.repoinspect.job;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 압축 파일 본문 읽기를 감시한다. HttpClient 에는 본문 읽기 시간 상한이 없어서, 바이트가 idle 동안 오지 않거나
 * 전체가 total 을 넘으면 다른 스레드에서 스트림을 닫아 막힌 read 를 IOException 으로 깨운다.
 * 느리지만 계속 오는 큰 압축 파일은 idle 에 걸리지 않고, 끝없이 찔끔 오는 응답은 total 에 걸린다.
 */
final class WatchedInputStream extends FilterInputStream {

  static final long CHECK_MILLIS = 200;

  private final long idleNanos;
  private final long totalNanos;
  private final long startNanos;
  private volatile long lastProgressNanos;
  private final ScheduledFuture<?> check;

  WatchedInputStream(InputStream in, Duration idle, Duration total, ScheduledExecutorService timer) {
    super(in);
    this.idleNanos = idle.toNanos();
    this.totalNanos = total.toNanos();
    this.startNanos = System.nanoTime();
    this.lastProgressNanos = startNanos;
    this.check = timer.scheduleAtFixedRate(this::checkDeadline, CHECK_MILLIS, CHECK_MILLIS, TimeUnit.MILLISECONDS);
  }

  @Override
  public int read() throws IOException {
    int b = super.read();
    if (b >= 0) {
      lastProgressNanos = System.nanoTime();
    }
    return b;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int n = super.read(b, off, len);
    if (n > 0) {
      lastProgressNanos = System.nanoTime();
    }
    return n;
  }

  @Override
  public void close() throws IOException {
    check.cancel(false);
    super.close();
  }

  private void checkDeadline() {
    long now = System.nanoTime();
    if (now - lastProgressNanos > idleNanos || now - startNanos > totalNanos) {
      try {
        close();
      } catch (IOException | RuntimeException ignored) {
        // 닫기 실패는 읽는 쪽 오류로 드러난다. 여기서 던지면 감시 작업이 조용히 멈춘다
      }
    }
  }
}
