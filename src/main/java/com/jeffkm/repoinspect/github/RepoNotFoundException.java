package com.jeffkm.repoinspect.github;

/** 없거나 비공개인 저장소. GitHub 는 둘을 구분하지 않고 404 를 준다. */
public class RepoNotFoundException extends RuntimeException {

  public RepoNotFoundException(String path) {
    super("저장소를 찾을 수 없습니다: " + path);
  }
}
