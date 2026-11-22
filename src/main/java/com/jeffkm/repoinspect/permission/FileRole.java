package com.jeffkm.repoinspect.permission;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 파일의 종류(kind)와 위치(mainLocation). 규칙을 어디에 적용할지, 근거를 주요/참고 중 어디에 둘지 정한다.
 * 참고로 둔 근거도 지우지 않는다. 테스트 폴더에 둔 코드를 본 코드가 불러 쓸 수도 있어서다.
 */
public record FileRole(Kind kind, boolean mainLocation) {

  public enum Kind { CODE, INSTRUCTION, CONFIG, DOC }

  private static final Set<String> CODE_EXT = Set.of("js", "mjs", "cjs", "ts", "mts", "cts", "jsx", "tsx", "py",
      "java", "kt", "kts", "go", "rs", "rb", "php", "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "cs", "swift",
      "lua", "pl");
  private static final Set<String> DOC_EXT = Set.of("md", "mdx", "txt", "rst", "adoc");
  // 에이전트가 지시로 읽는 파일. 설명문이 아니라 실행 지시라서 문서와 따로 본다
  private static final Set<String> INSTRUCTION_FILES = Set.of("SKILL.md", "CLAUDE.md", "AGENTS.md", "GEMINI.md");
  private static final Set<String> INSTRUCTION_DIRS = Set.of("commands", "agents");
  private static final Set<String> SIDE_DIRS = Set.of("test", "tests", "__tests__", "spec", "specs", "testdata",
      "test-data", "fixtures", "__fixtures__", "__mocks__", "e2e", "example", "examples", "sample", "samples", "demo",
      "demos", "doc", "docs", "benchmark", "benchmarks", ".github");
  // 대소문자를 구분한다. Contest.java 같은 이름이 테스트로 잡히지 않게
  private static final Pattern TEST_FILE = Pattern.compile(
      "test_.*\\.py|.*_test\\.(py|go|rb)|.*\\.(test|spec)\\.[cm]?[jt]sx?|.*Tests?\\.(java|kt)|conftest\\.py");

  public static FileRole of(String path, String content) {
    String[] segments = path.split("/");
    String base = segments[segments.length - 1];
    String parent = segments.length > 1 ? segments[segments.length - 2] : "";
    boolean main = !TEST_FILE.matcher(base).matches();
    for (int i = 0; i < segments.length - 1 && main; i++) {
      main = !SIDE_DIRS.contains(segments[i].toLowerCase(Locale.ROOT));
    }
    return new FileRole(kindOf(base, parent, content), main);
  }

  private static Kind kindOf(String base, String parent, String content) {
    int dot = base.lastIndexOf('.');
    String ext = dot < 0 ? "" : base.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (INSTRUCTION_FILES.contains(base) || (ext.equals("md") && INSTRUCTION_DIRS.contains(parent))) {
      return Kind.INSTRUCTION;
    }
    if (CODE_EXT.contains(ext) || (dot < 0 && content.startsWith("#!"))) {
      return Kind.CODE;
    }
    if (DOC_EXT.contains(ext)) {
      return Kind.DOC;
    }
    return Kind.CONFIG;
  }

  /** 문서의 설치 안내(curl | bash 등)는 사용자가 그대로 따라 하므로 본 위치면 주요로 본다. */
  public boolean isPrimary(Category category) {
    return mainLocation && (kind != Kind.DOC || category == Category.INSTALL_SCRIPT);
  }
}
