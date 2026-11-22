package com.jeffkm.repoinspect.permission;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SKILL.md 앞머리(첫 줄 --- 부터 다음 --- 까지)의 allowed-tools 에 Bash 가 있으면 그 줄 번호(1부터).
 * 한 줄 표기(allowed-tools: Read, Bash)와 YAML 목록 표기(- Bash)를 모두 본다. 본문에 나온 allowed-tools 는 설명문이라 보지 않는다.
 */
final class SkillFrontmatter {

  private static final Pattern KEY = Pattern.compile("(?i)^allowed-tools\\s*:(.*)$");
  private static final Pattern BASH = Pattern.compile("(?i)\\bBash\\b");

  private SkillFrontmatter() {
  }

  static OptionalInt bashToolLine(String[] lines) {
    if (lines.length == 0 || !lines[0].replace("\uFEFF", "").strip().equals("---")) {
      return OptionalInt.empty();
    }
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].strip().equals("---")) {
        return OptionalInt.empty();
      }
      Matcher key = KEY.matcher(lines[i]);
      if (!key.find()) {
        continue;
      }
      // 한 줄·목록·블록 스칼라·여러 줄 흐름 표기 모두 "키 뒤 값 + 이어지는 줄" 에서 처음 나오는 Bash 를 찾는다
      if (BASH.matcher(key.group(1)).find()) {
        return OptionalInt.of(i + 1);
      }
      for (int j = i + 1; j < lines.length; j++) {
        String item = lines[j];
        if (item.strip().equals("---")) {
          return OptionalInt.empty();
        }
        if (item.isBlank() || item.startsWith("#")) {
          continue;
        }
        // 들여쓰기도 - 도 아니면 다음 키다. 이어지는 줄이 끝났다
        if (!item.startsWith(" ") && !item.startsWith("\t") && !item.startsWith("-")) {
          return OptionalInt.empty();
        }
        if (BASH.matcher(item).find()) {
          return OptionalInt.of(j + 1);
        }
      }
      return OptionalInt.empty();
    }
    return OptionalInt.empty();
  }
}
