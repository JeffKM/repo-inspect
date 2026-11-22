package com.jeffkm.repoinspect.github;

/** 사용자가 보낸 저장소 주소가 잘못됨. 메시지를 그대로 사용자에게 돌려줘도 안전하다. */
public class InvalidRepoUrlException extends IllegalArgumentException {

  public InvalidRepoUrlException(String message) {
    super(message);
  }
}
