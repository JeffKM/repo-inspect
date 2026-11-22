package com.jeffkm.repoinspect.archive;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 지나간 바이트 수를 세다가 상한을 넘으면 멈춘다. 받는 쪽과 풀린 쪽 양쪽에 건다. */
public class LimitedInputStream extends FilterInputStream {

  private final long limit;
  private long count;

  public LimitedInputStream(InputStream in, long limit) {
    super(in);
    this.limit = limit;
  }

  @Override
  public int read() throws IOException {
    int b = super.read();
    if (b >= 0) {
      add(1);
    }
    return b;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int n = super.read(b, off, len);
    if (n > 0) {
      add(n);
    }
    return n;
  }

  @Override
  public long skip(long n) throws IOException {
    long skipped = super.skip(n);
    add(skipped);
    return skipped;
  }

  private void add(long n) throws LimitExceededException {
    count += n;
    if (count > limit) {
      throw new LimitExceededException(limit);
    }
  }
}
