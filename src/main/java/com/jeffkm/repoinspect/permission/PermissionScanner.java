package com.jeffkm.repoinspect.permission;

import com.jeffkm.repoinspect.archive.SourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 파일을 줄 단위로 훑어 규칙에 걸린 줄을 근거로 남긴다. 코드를 실행하지 않는다. */
public final class PermissionScanner {

  public static final int MAX_FINDINGS = 500;
  private static final int SNIPPET_MAX = 200;
  private static final Pattern URL = Pattern.compile("https?://([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)");
  private static final Set<String> LOCK_FILES =
      Set.of("package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock", "Cargo.lock", "uv.lock");

  private PermissionScanner() {
  }

  public static ScanResult scan(List<SourceFile> files) {
    List<Finding> found = new ArrayList<>();
    Set<String> domains = new TreeSet<>();
    boolean truncated = false;
    for (SourceFile f : files) {
      String base = f.path().substring(f.path().lastIndexOf('/') + 1);
      if (LOCK_FILES.contains(base)) {
        continue;
      }
      // README 같은 문서의 링크는 소음이다. SKILL.md 는 에이전트가 따르는 지시라 링크도 본다
      boolean collectDomains = !base.endsWith(".md") || base.equals("SKILL.md");
      String[] lines = f.content().split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        for (Rule rule : PermissionRules.ALL) {
          if (!rule.appliesTo(f.path()) || !rule.pattern().matcher(line).find()) {
            continue;
          }
          if (found.size() >= MAX_FINDINGS) {
            truncated = true;
            continue;
          }
          found.add(new Finding(rule.category().name(), rule.id(), f.path(), i + 1, snippet(line)));
        }
        if (collectDomains) {
          Matcher m = URL.matcher(line);
          while (m.find()) {
            domains.add(m.group(1).toLowerCase(Locale.ROOT));
          }
        }
      }
    }
    return new ScanResult(found, domains, truncated);
  }

  static String snippet(String line) {
    String t = line.strip();
    return t.length() <= SNIPPET_MAX ? t : t.substring(0, SNIPPET_MAX) + "…";
  }
}
