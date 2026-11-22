package com.jeffkm.repoinspect.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeffkm.repoinspect.support.TarGz;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SafeTarReaderTest {

  static final ArchiveLimits LIMITS = new ArchiveLimits(1_000_000, 10, 100_000);

  @Test
  void 최상위_폴더를_떼고_텍스트_파일을_읽는다() throws IOException {
    var in = new TarGz()
        .file("acme-tool-abc123/README.md", "# 도구")
        .file("acme-tool-abc123/src/index.js", "console.log(1)")
        .stream();

    ReadResult result = SafeTarReader.read(in, LIMITS);

    assertThat(result.truncated()).isFalse();
    assertThat(result.files()).containsExactly(
        new SourceFile("README.md", "# 도구"),
        new SourceFile("src/index.js", "console.log(1)"));
  }

  @Test
  void 경로_탈출과_절대_경로는_무시한다() throws IOException {
    var in = new TarGz()
        .file("top/../../evil.sh", "rm -rf /")
        .file("../outside.txt", "x")
        .file("/etc/passwd", "root")
        .file("top/ok.txt", "ok")
        .stream();

    ReadResult result = SafeTarReader.read(in, LIMITS);

    assertThat(result.files()).extracting(SourceFile::path).containsExactly("ok.txt");
  }

  @Test
  void 심볼릭_링크는_따라가지_않는다() throws IOException {
    var in = new TarGz()
        .symlink("top/link", "/etc/passwd")
        .file("top/a.txt", "a")
        .stream();

    ReadResult result = SafeTarReader.read(in, LIMITS);

    assertThat(result.files()).extracting(SourceFile::path).containsExactly("a.txt");
  }

  @Test
  void 바이너리와_큰_파일은_건너뛴다() throws IOException {
    var in = new TarGz()
        .file("top/image.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 1})
        .file("top/big.txt", "x".repeat(100_001))
        .file("top/small.txt", "small")
        .stream();

    ReadResult result = SafeTarReader.read(in, LIMITS);

    assertThat(result.files()).extracting(SourceFile::path).containsExactly("small.txt");
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void 압축_폭탄은_전체_크기_상한에서_멈추고_일부만_분석으로_표시한다() throws IOException {
    var in = new TarGz()
        .file("top/first.txt", "first")
        .file("top/zeros.bin", new byte[10_000_000])
        .file("top/after.txt", "after")
        .stream();

    ReadResult result = SafeTarReader.read(in, LIMITS);

    assertThat(result.truncated()).isTrue();
    assertThat(result.files()).extracting(SourceFile::path).containsExactly("first.txt");
  }

  @Test
  void 파일_수_상한을_넘으면_앞부분만_읽는다() throws IOException {
    var limits = new ArchiveLimits(1_000_000, 2, 100_000);
    var in = new TarGz()
        .file("top/1.txt", "1")
        .file("top/2.txt", "2")
        .file("top/3.txt", "3")
        .stream();

    ReadResult result = SafeTarReader.read(in, limits);

    assertThat(result.truncated()).isTrue();
    assertThat(result.files()).hasSize(2);
  }

  @Test
  void 받는_스트림이_상한을_넘으면_일부만_분석으로_표시한다() throws IOException {
    byte[] tarGz = new TarGz().file("top/a.txt", "a".repeat(50_000)).stream().readAllBytes();
    var limitedDownload = new LimitedInputStream(new ByteArrayInputStream(tarGz), 10);

    ReadResult result = SafeTarReader.read(limitedDownload, LIMITS);

    assertThat(result.truncated()).isTrue();
  }
}
