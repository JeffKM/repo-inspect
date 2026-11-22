package com.jeffkm.repoinspect.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WatchedInputStreamTest {

  final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

  @AfterEach
  void stop() {
    timer.shutdownNow();
  }

  /** 100ms 마다 1바이트. count 개를 보내면 끝(-1), count 가 음수면 끝없음. 닫히면 IOException. */
  static final class Trickle extends InputStream {
    private final int count;
    private int sent;
    private volatile boolean closed;

    Trickle(int count) {
      this.count = count;
    }

    @Override
    public int read() throws IOException {
      if (closed) {
        throw new IOException("closed");
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
      }
      if (closed) {
        throw new IOException("closed");
      }
      if (count >= 0 && sent >= count) {
        return -1;
      }
      sent++;
      return 'x';
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  /** 닫힐 때까지 아무것도 보내지 않는다. */
  static final class Stalled extends InputStream {
    private final CountDownLatch closed = new CountDownLatch(1);

    @Override
    public int read() throws IOException {
      try {
        closed.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      throw new IOException("closed");
    }

    @Override
    public void close() {
      closed.countDown();
    }
  }

  @Test
  void 바이트가_안_오면_무응답_기한에_닫는다() {
    var w = new WatchedInputStream(new Stalled(), Duration.ofMillis(300), Duration.ofSeconds(10), timer);

    assertTimeoutPreemptively(Duration.ofSeconds(3),
        () -> assertThatThrownBy(w::read).isInstanceOf(IOException.class));
  }

  @Test
  void 느려도_계속_오면_끝까지_읽는다() throws IOException {
    var w = new WatchedInputStream(new Trickle(10), Duration.ofMillis(300), Duration.ofSeconds(10), timer);

    int n = 0;
    while (w.read() >= 0) {
      n++;
    }

    assertThat(n).isEqualTo(10);
  }

  @Test
  void 끝없이_찔끔_오면_전체_기한에_닫는다() {
    var w = new WatchedInputStream(new Trickle(-1), Duration.ofMillis(300), Duration.ofSeconds(1), timer);
    long start = System.nanoTime();

    assertTimeoutPreemptively(Duration.ofSeconds(4), () -> assertThatThrownBy(() -> {
      while (true) {
        w.read();
      }
    }).isInstanceOf(IOException.class));
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }
}
