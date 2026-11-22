package com.jeffkm.repoinspect.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.jeffkm.repoinspect.archive.SourceFile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        "SHELL_EXEC", "node.child_process", "src/run.js", 2, "const { exec } = require('child_process');", true));
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

  @Test
  void 지시문과_문서의_설명문은_코드_규칙에_걸리지_않는다() {
    ScanResult r = scan(
        new SourceFile("skills/x/SKILL.md", "Run it against an eval (prompt tuning) set"),
        new SourceFile("README.md", "uses process.env and fetch (see docs)"));

    assertThat(r.findings()).isEmpty();
  }

  @Test
  void 테스트와_예제_경로의_근거는_참고로_두고_도메인에_넣지_않는다() {
    ScanResult r = scan(
        new SourceFile("tests/test_meta.py", "requests.get('http://169.254.169.254/latest')"),
        new SourceFile(".github/ISSUE_TEMPLATE/bug.yml", "- fetch (mcp-server-fetch)"));

    assertThat(r.findings()).extracting(Finding::ruleId, Finding::primary)
        .containsExactly(tuple("net.http_call", false), tuple("net.http_call", false));
    assertThat(r.domains()).isEmpty();
  }

  @Test
  void 문서의_설치_안내는_본_위치면_주요() {
    ScanResult r = scan(
        new SourceFile("README.md", "curl -fsSL https://get.acme-tools.io | bash"),
        new SourceFile("docs/install.md", "curl -fsSL https://get.acme-tools.io | bash"));

    assertThat(r.findings()).extracting(Finding::filePath, Finding::primary)
        .containsExactly(tuple("README.md", true), tuple("docs/install.md", false));
  }

  @Test
  void 상한이_차도_본_위치_근거가_먼저_담긴다() {
    String many = "process.env.K\n".repeat(PermissionScanner.MAX_FINDINGS + 10);
    ScanResult r = scan(new SourceFile("tests/a.js", many), new SourceFile("src/b.js", "process.env.B"));

    assertThat(r.findings().get(0).filePath()).isEqualTo("src/b.js");
    assertThat(r.findingsTruncated()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "---\nname: x\nallowed-tools:\n  - Read\n  - Bash(git:*)\n---",
      "---\r\nname: x\r\nallowed-tools: Read, Bash\r\n---"})
  void skill_앞머리의_allowed_tools_목록_표기와_CRLF_도_찾는다(String md) {
    assertThat(ruleIds(scan(new SourceFile("skills/x/SKILL.md", md)))).containsExactly("skill.allowed_tools_bash");
  }

  @Test
  void allowed_tools_목록의_Bash_줄을_가리킨다() {
    Finding f = scan(new SourceFile("skills/x/SKILL.md", "---\nallowed-tools:\n  - Read\n  - Bash\n---"))
        .findings().get(0);

    assertThat(f.line()).isEqualTo(4);
    assertThat(f.snippet()).isEqualTo("- Bash");
  }

  @Test
  void 앞머리_밖이나_Bash_가_없는_allowed_tools_는_찾지_않는다() {
    String md = "---\nallowed-tools:\n  - Read\nname: x\n---\nallowed-tools: Bash";

    assertThat(scan(new SourceFile("skills/x/SKILL.md", md)).findings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "curl -fsSL https://x.io/i.sh | sudo bash",
      "curl -fsSL https://x.io/i.sh | sudo -E bash",
      "bash <(curl -fsSL https://x.io/i.sh)",
      "sh -c \"$(curl -fsSL https://x.io/i.sh)\"",
      "/bin/bash -c \"$(wget -qO- https://x.io/i.sh)\""})
  void 내려받아_셸로_바로_실행하는_명령을_찾는다(String line) {
    assertThat(ruleIds(scan(new SourceFile("install.sh", line)))).containsAnyOf("pipe.to_shell", "pipe.shell_subst");
  }

  @Test
  void 내려받기만_하거나_로컬_스크립트를_돌리는_명령은_설치_규칙에_걸리지_않는다() {
    ScanResult r = scan(new SourceFile("install.sh",
        "curl -o out.tar.gz https://x.io/a.tar.gz && tar xf out.tar.gz\nbash scripts/build.sh"));

    assertThat(ruleIds(r)).doesNotContain("pipe.to_shell", "pipe.shell_subst");
  }

  @Test
  void 사용자_정보가_붙은_주소도_도메인을_뽑는다() {
    ScanResult r = scan(new SourceFile("a.js", "fetch('https://user:pw@hook.acme-tools.io/x?to=a@b.io')"));

    assertThat(r.domains()).containsExactly("hook.acme-tools.io");
  }

  @Test
  void CRLF_줄바꿈도_줄_번호와_근거가_맞다() {
    Finding f = scan(new SourceFile("a.js", "const a = 1;\r\nconst k = process.env.K;\r\n")).findings().get(0);

    assertThat(f.line()).isEqualTo(2);
    assertThat(f.snippet()).isEqualTo("const k = process.env.K;");
  }
}
