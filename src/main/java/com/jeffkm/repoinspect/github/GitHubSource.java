package com.jeffkm.repoinspect.github;

import java.io.InputStream;

/** 나머지 코드는 이 인터페이스만 안다. 테스트에서는 가짜로 바꾼다. */
public interface GitHubSource {

  /** 기본 브랜치의 최신 커밋 SHA(40자). */
  String resolveHeadSha(RepoRef repo);

  /** 그 커밋의 tar.gz 스트림. 크기 상한이 걸려 있다. 닫는 책임은 호출한 쪽. */
  InputStream openTarball(RepoRef repo, String sha);
}
