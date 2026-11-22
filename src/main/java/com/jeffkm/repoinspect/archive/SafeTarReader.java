package com.jeffkm.repoinspect.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * GitHub 압축 파일(tar.gz)을 디스크에 풀지 않고 읽는다. 저장소 코드는 실행하지 않는다.
 * 막는 것: 경로 탈출(..), 절대 경로, 심볼릭/하드 링크, 압축 폭탄(풀린 전체 크기 상한), 파일 수 폭주.
 */
public final class SafeTarReader {

  private static final int BINARY_SNIFF_BYTES = 8000;

  private SafeTarReader() {
  }

  public static ReadResult read(InputStream tarGz, ArchiveLimits limits) throws IOException {
    List<SourceFile> files = new ArrayList<>();
    int seen = 0;
    int skipped = 0;
    boolean oversized = false;
    try (TarArchiveInputStream tar = new TarArchiveInputStream(
        new LimitedInputStream(new GzipCompressorInputStream(tarGz), limits.maxTotalBytes()))) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        // isFile() 은 링크, FIFO, 장치 항목에도 true 라 따로 거른다
        if (!entry.isFile() || entry.isSymbolicLink() || entry.isLink() || entry.isFIFO()
            || entry.isCharacterDevice() || entry.isBlockDevice()) {
          continue;
        }
        String path = normalize(entry.getName());
        if (path == null) {
          continue;
        }
        if (++seen > limits.maxFiles()) {
          return new ReadResult(files, true, skipped);
        }
        if (entry.getSize() > limits.maxFileBytes()) {
          skipped++;
          oversized = true;
          continue;
        }
        byte[] bytes = tar.readNBytes((int) entry.getSize());
        if (isBinary(bytes)) {
          skipped++;
          continue;
        }
        files.add(new SourceFile(path, new String(bytes, StandardCharsets.UTF_8)));
      }
      return new ReadResult(files, oversized, skipped);
    } catch (LimitExceededException e) {
      return new ReadResult(files, true, skipped);
    }
  }

  /** 최상위 폴더(owner-repo-sha)를 떼고, 위험한 경로면 null. */
  static String normalize(String name) {
    if (name.startsWith("/") || name.contains("\\")) {
      return null;
    }
    String[] segments = name.split("/");
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      String s = segments[i];
      if (s.equals("..")) {
        return null;
      }
      if (i == 0 || s.isEmpty() || s.equals(".")) {
        continue;
      }
      if (!out.isEmpty()) {
        out.append('/');
      }
      out.append(s);
    }
    return out.isEmpty() ? null : out.toString();
  }

  private static boolean isBinary(byte[] bytes) {
    int n = Math.min(bytes.length, BINARY_SNIFF_BYTES);
    for (int i = 0; i < n; i++) {
      if (bytes[i] == 0) {
        return true;
      }
    }
    return false;
  }
}
