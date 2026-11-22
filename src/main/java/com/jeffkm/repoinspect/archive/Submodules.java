package com.jeffkm.repoinspect.archive;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GitHub 압축 파일에는 서브모듈 내용이 들어 있지 않다. 루트 .gitmodules 의 path 를 모아 "분석하지 않은 부분"으로 알린다. */
public final class Submodules {

  static final int MAX = 50;
  private static final Pattern PATH = Pattern.compile("^\\s*path\\s*=\\s*(.+?)\\s*$");

  private Submodules() {
  }

  public static List<String> paths(List<SourceFile> files) {
    return files.stream()
        .filter(f -> f.path().equals(".gitmodules"))
        .flatMap(f -> f.content().lines())
        .map(PATH::matcher)
        .filter(Matcher::matches)
        .map(m -> m.group(1))
        .limit(MAX)
        .toList();
  }
}
