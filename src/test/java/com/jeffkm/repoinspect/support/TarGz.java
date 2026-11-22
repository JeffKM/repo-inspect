package com.jeffkm.repoinspect.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/** 테스트용 tar.gz 를 메모리에서 만든다. 경로 탈출, 심볼릭 링크 같은 위험한 항목도 일부러 넣을 수 있다. */
public final class TarGz {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final TarArchiveOutputStream tar;

  public TarGz() {
    try {
      tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes));
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public TarGz file(String name, String content) {
    return file(name, content.getBytes(StandardCharsets.UTF_8));
  }

  public TarGz file(String name, byte[] content) {
    // true: 절대 경로나 .. 를 고치지 않고 그대로 넣는다(공격 재현용)
    TarArchiveEntry entry = new TarArchiveEntry(name, true);
    entry.setSize(content.length);
    put(entry, content);
    return this;
  }

  public TarGz symlink(String name, String target) {
    TarArchiveEntry entry = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
    entry.setLinkName(target);
    put(entry, new byte[0]);
    return this;
  }

  public InputStream stream() {
    try {
      tar.finish();
      tar.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new ByteArrayInputStream(bytes.toByteArray());
  }

  private void put(TarArchiveEntry entry, byte[] content) {
    try {
      tar.putArchiveEntry(entry);
      tar.write(content);
      tar.closeArchiveEntry();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
