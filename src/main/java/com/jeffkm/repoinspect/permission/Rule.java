package com.jeffkm.repoinspect.permission;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/** 한 줄에 적용하는 탐지 규칙. kinds 의 파일 종류에만, onlyFile 이 있으면 그 이름의 파일에만 적용한다. */
public record Rule(String id, Category category, Pattern pattern, String onlyFile, Set<FileRole.Kind> kinds) {

  static Rule of(String id, Category category, String regex, FileRole.Kind... kinds) {
    return new Rule(id, category, Pattern.compile(regex), null, Set.of(kinds));
  }

  static Rule onlyIn(String fileName, String id, Category category, String regex) {
    return new Rule(id, category, Pattern.compile(regex), fileName, EnumSet.allOf(FileRole.Kind.class));
  }

  boolean appliesTo(String path, FileRole.Kind kind) {
    return kinds.contains(kind) && (onlyFile == null || path.equals(onlyFile) || path.endsWith("/" + onlyFile));
  }
}
