package com.jeffkm.repoinspect.github;

import java.util.regex.Pattern;

/** GitHub 저장소 하나. 사용자가 붙여 넣은 주소를 여기로 바꿔야만 안으로 들어온다(신뢰 경계). */
public record RepoRef(String owner, String name) {

  private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.-]{1,100}");
  private static final String HOST = "github.com/";

  public static RepoRef parse(String input) {
    if (input == null || input.isBlank()) {
      throw new IllegalArgumentException("저장소 주소가 비었습니다");
    }
    String s = input.strip().split("[?#]", 2)[0];
    s = s.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
    if (!s.startsWith(HOST)) {
      throw new IllegalArgumentException("github.com 저장소 주소만 받습니다");
    }
    String[] parts = s.substring(HOST.length()).split("/");
    if (parts.length < 2) {
      throw new IllegalArgumentException("주소에 소유자와 저장소 이름이 모두 있어야 합니다");
    }
    String owner = parts[0];
    String name = parts[1].replaceFirst("\\.git$", "");
    if (!valid(owner) || !valid(name)) {
      throw new IllegalArgumentException("저장소 이름 형식이 올바르지 않습니다");
    }
    return new RepoRef(owner, name);
  }

  private static boolean valid(String part) {
    return PART.matcher(part).matches() && !part.equals(".") && !part.equals("..");
  }
}
