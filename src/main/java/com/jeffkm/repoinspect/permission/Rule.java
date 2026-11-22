package com.jeffkm.repoinspect.permission;

import java.util.regex.Pattern;

/** 한 줄에 적용하는 탐지 규칙. onlyFile 이 있으면 그 이름의 파일에만 적용한다. */
public record Rule(String id, Category category, Pattern pattern, String onlyFile) {

  static Rule of(String id, Category category, String regex) {
    return new Rule(id, category, Pattern.compile(regex), null);
  }

  static Rule onlyIn(String fileName, String id, Category category, String regex) {
    return new Rule(id, category, Pattern.compile(regex), fileName);
  }

  boolean appliesTo(String path) {
    return onlyFile == null || path.equals(onlyFile) || path.endsWith("/" + onlyFile);
  }
}
