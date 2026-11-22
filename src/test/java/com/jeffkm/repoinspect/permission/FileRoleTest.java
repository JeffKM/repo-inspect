package com.jeffkm.repoinspect.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FileRoleTest {

  @ParameterizedTest
  @CsvSource({
      "src/index.js, CODE, true",
      "skills/pdf/SKILL.md, INSTRUCTION, true",
      "CLAUDE.md, INSTRUCTION, true",
      ".claude/commands/review.md, INSTRUCTION, true",
      "plugins/x/agents/reviewer.md, INSTRUCTION, true",
      "README.md, DOC, true",
      "docs/install.md, DOC, false",
      "tests/test_api.py, CODE, false",
      "src/api.test.ts, CODE, false",
      "src/main/java/a/FooTest.java, CODE, false",
      "src/Contest.java, CODE, true",
      "src/main/java/com/example/demo/App.java, CODE, true",
      "src/main/kotlin/sample/Run.kt, CODE, true",
      "examples/src/main/java/App.java, CODE, false",
      "pkg/server_test.go, CODE, false",
      "examples/client.py, CODE, false",
      "src/__mocks__/fs.js, CODE, false",
      ".github/ISSUE_TEMPLATE/bug.yml, CONFIG, false",
      "package.json, CONFIG, true",
      "hooks/hooks.json, CONFIG, true",
      "web/App.vue, CODE, true",
      "index.html, CODE, true",
      "native/a.c, CODE, true",
      "lib/payload.dat, CODE, true",
      "Dockerfile, CONFIG, true",
      "Makefile, CONFIG, true",
      "requirements.txt, CONFIG, true",
      "requirements-dev.txt, CONFIG, true",
      "notes.txt, DOC, true",
      "config/app.yaml, CONFIG, true",
      "assets/logo.svg, CONFIG, true",
      ".npmrc, CONFIG, true",
      ".claude/commands/git/commit.md, INSTRUCTION, true",
      "skills/pdf/reference.md, INSTRUCTION, true",
      "docs/agents/intro.md, INSTRUCTION, false",
      "src/README.md, DOC, true"})
  void 경로로_종류와_위치를_정한다(String path, FileRole.Kind kind, boolean main) {
    assertThat(FileRole.of(path, "")).isEqualTo(new FileRole(kind, main));
  }

  @Test
  void 확장자_없는_파일은_셔뱅이_있으면_코드() {
    assertThat(FileRole.of("bin/run", "#!/bin/sh\necho hi").kind()).isEqualTo(FileRole.Kind.CODE);
    assertThat(FileRole.of("LICENSE", "MIT License").kind()).isEqualTo(FileRole.Kind.CONFIG);
    assertThat(FileRole.of("bin/tool", "#!/bin/sh").kind()).isEqualTo(FileRole.Kind.CODE);
    assertThat(FileRole.of("bin/tool", "\uFEFF#!/bin/sh").kind()).isEqualTo(FileRole.Kind.CODE);
  }

  @Test
  void 본_위치의_코드는_주요_테스트_위치는_참고() {
    assertThat(FileRole.of("src/a.js", "").isPrimary(Category.NETWORK)).isTrue();
    assertThat(FileRole.of("tests/a.js", "").isPrimary(Category.NETWORK)).isFalse();
  }

  @Test
  void 문서는_설치_명령만_주요() {
    FileRole readme = FileRole.of("README.md", "");
    assertThat(readme.isPrimary(Category.INSTALL_SCRIPT)).isTrue();
    assertThat(readme.isPrimary(Category.NETWORK)).isFalse();
    assertThat(FileRole.of("docs/install.md", "").isPrimary(Category.INSTALL_SCRIPT)).isFalse();
  }
}
