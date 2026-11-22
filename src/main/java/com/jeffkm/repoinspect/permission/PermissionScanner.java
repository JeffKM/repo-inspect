package com.jeffkm.repoinspect.permission;

import com.jeffkm.repoinspect.archive.SourceFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 파일을 줄 단위로 훑어 규칙에 걸린 줄을 근거로 남긴다. 코드를 실행하지 않는다. */
public final class PermissionScanner {

  public static final int MAX_FINDINGS = 500;
  public static final int MAX_DOMAINS = 200;
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
    boolean domainsTruncated = false;
    // 테스트·예제 근거가 상한을 먼저 채워 본 코드 근거가 밀려나지 않게 본 위치 파일부터 훑는다(정렬은 안정적)
    List<SourceFile> ordered = files.stream()
        .sorted(Comparator.comparing((SourceFile f) -> !FileRole.of(f.path(), f.content()).mainLocation()))
        .toList();
    for (SourceFile f : ordered) {
      String base = f.path().substring(f.path().lastIndexOf('/') + 1);
      if (LOCK_FILES.contains(base)) {
        continue;
      }
      FileRole role = FileRole.of(f.path(), f.content());
      // 문서의 링크와 테스트·예제의 주소는 소음이다. 에이전트 지시문(SKILL.md 등)과 설정의 주소는 본다
      boolean collectDomains = role.mainLocation() && role.kind() != FileRole.Kind.DOC;
      String[] lines = f.content().split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        for (Rule rule : PermissionRules.ALL) {
          if (!rule.appliesTo(f.path(), role.kind())) {
            continue;
          }
          Matcher rm = rule.pattern().matcher(line);
          if (!rm.find()) {
            continue;
          }
          if (found.size() >= MAX_FINDINGS) {
            truncated = true;
            continue;
          }
          found.add(new Finding(rule.category().name(), rule.id(), f.path(), i + 1, snippet(line, rm.start()),
              role.isPrimary(rule.category())));
        }
        if (collectDomains) {
          Matcher m = URL.matcher(line);
          while (m.find()) {
            String d = m.group(1).toLowerCase(Locale.ROOT);
            if (domains.size() < MAX_DOMAINS) {
              domains.add(d);
            } else if (!domains.contains(d)) {
              domainsTruncated = true;
            }
          }
        }
      }
    }
    return new ScanResult(found, domains, truncated, domainsTruncated);
  }

  /** 200자 이하면 그대로, 길면 걸린 위치(matchStart, 원본 줄 기준) 앞 40자부터 200자 창을 잘라 보여준다. */
  static String snippet(String line, int matchStart) {
    String t = line.strip().replace('\u0000', '\uFFFD'); // NUL 은 PostgreSQL text 에 저장되지 않는다
    if (t.length() <= SNIPPET_MAX) {
      return t;
    }
    int lead = line.length() - line.stripLeading().length();
    int start = Math.max(0, Math.min(matchStart - lead, t.length() - 1) - 40);
    int end = Math.min(t.length(), start + SNIPPET_MAX);
    return (start > 0 ? "…" : "") + t.substring(start, end) + (end < t.length() ? "…" : "");
  }
}
