package com.jeffkm.repoinspect.github;

/** 커밋이 하나도 없는 저장소. GitHub 는 커밋 조회에 409 를 준다. */
public class EmptyRepositoryException extends GitHubException {

  public EmptyRepositoryException(String path) {
    super(409, "빈 저장소입니다: " + path);
  }
}
